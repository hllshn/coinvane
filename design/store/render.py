#!/usr/bin/env python3
"""Coinvane Play Store ekran görüntülerini üretir (11 dil × 6 ekran, 1080×1920 JPEG).

Kullanım (repo kökünden):  python3 design/store/render.py [--only en-US,tr-TR] [--shots 1,3]

1. Binance'ten anlık fiyatları ve BTC 1 saatlik mumlarını, ExchangeRate-API'den kurları çeker.
2. Uygulamanın kendi metinlerini app/src/main/res/values*/strings.xml dosyalarından okur.
3. design/store/data.js'i yazar (v8: delist filtresi, piyasa hareketi, dönem verisi) ve screenshots.html'i her dil/ekran için headless Chrome ile fotoğraflar.
Çıktı: fastlane/metadata/android/<locale>/images/{phone,sevenInch,tenInch}Screenshots/<n>.jpg
"""
import argparse, base64, json, os, re, subprocess, sys, tempfile, time, urllib.parse, urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HERE = os.path.join(ROOT, "design", "store")
RES = os.path.join(ROOT, "app", "src", "main", "res")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
REST = "https://data-api.binance.vision/api/v3/"
BASES = ["BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "LINK", "ADA", "SUI"]
NAMES = {"BTC": "Bitcoin", "ETH": "Ethereum", "SOL": "Solana", "XRP": "XRP", "BNB": "BNB", "DOGE": "Dogecoin",
         "LINK": "Chainlink", "ADA": "Cardano", "SUI": "Sui"}
FIAT = {"EUR", "GBP", "AUD", "TRY", "BRL", "JPY", "RUB", "UAH", "ARS", "ZAR", "PLN", "RON", "MXN", "COP", "CZK", "NGN", "IDR"}
CURRENCIES = ["USD", "EUR", "GBP", "TRY", "JPY", "KRW", "CNY", "INR", "BRL", "RUB", "UAH", "PLN", "CHF", "SEK",
              "NOK", "CAD", "AUD", "MXN", "ARS", "SAR", "AED", "IDR", "VND", "NGN", "ZAR"]

# Play locale → (res klasörü, BCP-47 biçim etiketi, para birimi, dil adı, sağdan sola)
LOCALES = {
    "en-US": ("values", "en-US", "USD", "English", False),
    "tr-TR": ("values-tr", "tr-TR", "TRY", "Türkçe", False),
    "de-DE": ("values-de", "de-DE", "EUR", "Deutsch", False),
    "fr-FR": ("values-fr", "fr-FR", "EUR", "Français", False),
    "es-ES": ("values-es", "es-ES", "EUR", "Español", False),
    "pt-BR": ("values-pt-rBR", "pt-BR", "BRL", "Português (Brasil)", False),
    "ru-RU": ("values-ru", "ru-RU", "RUB", "Русский", False),
    "ar": ("values-ar", "ar-SA", "SAR", "العربية", True),
    "ja-JP": ("values-ja", "ja-JP", "JPY", "日本語", False),
    "ko-KR": ("values-ko", "ko-KR", "KRW", "한국어", False),
    "zh-CN": ("values-zh-rCN", "zh-CN", "CNY", "简体中文", False),
}

# Ekran başlıkları: (başlık, alt satır) × 6 — ana sayfa, piyasalar, detay + alarm, portföy, widget, profil (v8)
CAPTIONS = {
    "en-US": [("Markets at a glance", "BTC, ETH and what’s rising or falling, on one screen."),
             ("Every coin, every period", "500+ coins. Gainers and losers from 5 minutes to 7 days."),
             ("Real charts, instant alerts", "Candles, volume and 5m–30d performance; alerts the moment a target is hit."),
             ("Your portfolio at a glance", "Total balance, 24h change and profit/loss."),
             ("Prices on your home screen", "A widget for your favorite coins."),
             ("Your currency, your language", "25 currencies and 11 languages.")],
    "tr-TR": [("Piyasa tek bakışta", "BTC, ETH ve yükselen ya da düşen her şey tek ekranda."),
             ("Her coin, her dönem", "500'den fazla coin. 5 dakikadan 7 güne yükselenler ve düşenler."),
             ("Gerçek grafik, anında alarm", "Mum, hacim ve 5dk–30g performans; hedefe ulaşıldığı an alarm."),
             ("Portföyün tek bakışta", "Toplam bakiye, 24 saatlik değişim ve kâr/zarar."),
             ("Fiyatlar ana ekranında", "Favori coinlerin için bir widget."),
             ("Senin paran, senin dilin", "25 para birimi ve 11 dil.")],
    "de-DE": [("Der Markt auf einen Blick", "BTC, ETH und alles, was steigt oder fällt, auf einem Bildschirm."),
             ("Jeder Coin, jeder Zeitraum", "Über 500 Coins. Gewinner und Verlierer von 5 Minuten bis 7 Tagen."),
             ("Echte Charts, sofortige Alarme", "Kerzen, Volumen und 5 Min.–30 Tage Performance; Alarm, sobald dein Ziel erreicht ist."),
             ("Dein Portfolio im Blick", "Gesamtsaldo, 24h-Änderung sowie Gewinn und Verlust."),
             ("Kurse auf dem Homescreen", "Ein Widget für deine Lieblingscoins."),
             ("Deine Währung, deine Sprache", "25 Währungen und 11 Sprachen.")],
    "fr-FR": [("Le marché en un coup d’œil", "BTC, ETH et tout ce qui monte ou baisse, sur un seul écran."),
             ("Chaque crypto, chaque période", "Plus de 500 cryptos. Hausses et baisses de 5 minutes à 7 jours."),
             ("Vrais graphiques, alertes immédiates", "Bougies, volume et performance 5 min–30 j ; alerte dès que l’objectif est atteint."),
             ("Votre portefeuille d’un coup d’œil", "Solde total, variation sur 24 h et plus/moins-values."),
             ("Les cours sur votre écran d’accueil", "Un widget pour vos cryptos favorites."),
             ("Votre devise, votre langue", "25 devises et 11 langues.")],
    "es-ES": [("El mercado de un vistazo", "BTC, ETH y todo lo que sube o baja, en una sola pantalla."),
             ("Cada moneda, cada periodo", "Más de 500 monedas. Ganadores y perdedores de 5 minutos a 7 días."),
             ("Gráficos reales, alertas al instante", "Velas, volumen y rendimiento de 5 min a 30 días; alerta en cuanto se alcanza tu objetivo."),
             ("Tu cartera de un vistazo", "Saldo total, cambio en 24 h y ganancias/pérdidas."),
             ("Precios en tu pantalla de inicio", "Un widget para tus monedas favoritas."),
             ("Tu moneda, tu idioma", "25 divisas y 11 idiomas.")],
    "pt-BR": [("O mercado num relance", "BTC, ETH e tudo o que sobe ou cai, em uma só tela."),
             ("Cada moeda, cada período", "Mais de 500 moedas. Altas e baixas de 5 minutos a 7 dias."),
             ("Gráficos reais, alertas na hora", "Velas, volume e desempenho de 5 min a 30 dias; alerta assim que o alvo é atingido."),
             ("Sua carteira num relance", "Saldo total, variação em 24h e lucro/prejuízo."),
             ("Preços na tela inicial", "Um widget para suas moedas favoritas."),
             ("Sua moeda, seu idioma", "25 moedas e 11 idiomas.")],
    "ru-RU": [("Рынок с первого взгляда", "BTC, ETH и всё, что растёт или падает, на одном экране."),
             ("Каждая монета, любой период", "Более 500 монет. Лидеры роста и падения от 5 минут до 7 дней."),
             ("Настоящие графики, мгновенные оповещения", "Свечи, объём и динамика от 5 мин до 30 дн; оповещение, как только цель достигнута."),
             ("Портфель с первого взгляда", "Общий баланс, изменение за 24 ч, прибыль и убытки."),
             ("Курсы на главном экране", "Виджет с избранными монетами."),
             ("Ваша валюта, ваш язык", "25 валют и 11 языков.")],
    "ar": [("السوق في لمحة", "BTC وETH وكل ما يرتفع أو ينخفض في شاشة واحدة."),
          ("كل عملة، كل فترة", "أكثر من 500 عملة. الأكثر ارتفاعًا وانخفاضًا من 5 دقائق إلى 7 أيام."),
          ("رسوم حقيقية وتنبيهات فورية", "شموع وحجم تداول وأداء من 5 دقائق إلى 30 يومًا، وتنبيه لحظة بلوغ هدفك."),
          ("محفظتك في لمحة", "إجمالي الرصيد وتغيّر 24 ساعة والربح والخسارة."),
          ("الأسعار على شاشتك الرئيسية", "أداة لعملاتك المفضلة."),
          ("عملتك ولغتك", "25 عملة و11 لغة.")],
    "ja-JP": [("マーケットをひと目で", "BTC、ETH、そして上昇・下落の全体像をひとつの画面で。"),
             ("すべてのコイン、すべての期間", "500以上のコイン。5分から7日までの値上がり・値下がり。"),
             ("本物のチャート、すぐ届くアラート", "ローソク足、出来高、5分〜30日のパフォーマンス。目標到達の瞬間に通知。"),
             ("資産をひと目で", "合計残高、24時間の変動、損益。"),
             ("ホーム画面に価格を", "お気に入りコインのウィジェット。"),
             ("あなたの通貨と言語で", "25通貨・11言語に対応。")],
    "ko-KR": [("시장을 한눈에", "BTC, ETH, 그리고 오르고 내리는 모든 것을 한 화면에."),
             ("모든 코인, 모든 기간", "500개 이상의 코인. 5분부터 7일까지 상승·하락 순위."),
             ("실제 차트, 즉시 알림", "캔들, 거래량, 5분~30일 성과. 목표 가격에 닿는 순간 알림."),
             ("포트폴리오를 한눈에", "총 잔액, 24시간 변동, 손익."),
             ("홈 화면에서 시세 확인", "즐겨찾는 코인 위젯."),
             ("내 통화, 내 언어로", "25개 통화, 11개 언어.")],
    "zh-CN": [("市场一目了然", "BTC、ETH 以及涨跌全貌，尽在一屏。"),
             ("每个币种，每个周期", "500 多个币种。5 分钟到 7 天的涨幅榜与跌幅榜。"),
             ("真实K线，即时提醒", "K线、成交量和 5 分钟至 30 天表现；价格到达目标即刻提醒。"),
             ("资产一目了然", "总资产、24 小时变化与盈亏。"),
             ("桌面即可看行情", "自选币种桌面小部件。"),
             ("您的货币，您的语言", "支持 25 种货币和 11 种语言。")],
}

# Örnek portföy (ekran görüntüsü için; gösterilen kâr/zarar canlı fiyatlarla hesaplanır)
PORTFOLIO = [{"base": "BTC", "amount": 0.12, "cost": 62000}, {"base": "ETH", "amount": 1.5, "cost": 2600},
             {"base": "SOL", "amount": 12, "cost": 120}, {"base": "LINK", "amount": 80, "cost": 9.5}]


def get(url, binary=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "coinvane-store"}), timeout=20) as r:
        data = r.read()
    return data if binary else json.loads(data)


def android_unescape(s):
    s = s.strip()
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    return s.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"').replace("\\@", "@").replace("\\\\", "\\")


def strings(folder):
    root = ET.parse(os.path.join(RES, folder, "strings.xml")).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            out[name] = android_unescape("".join(el.itertext()))
        elif el.tag == "plurals":
            items = {i.get("quantity"): android_unescape("".join(i.itertext())) for i in el}
            out[name] = items.get("other")
        elif el.tag == "string-array":
            out[name] = [android_unescape("".join(i.itertext())) for i in el]
    if "app_name" not in out:
        out["app_name"] = "Coinvane"
    return out


def ticker_coin(t):
    b = t["symbol"][:-4]
    return {"base": b, "name": NAMES.get(b, b), "last": float(t["lastPrice"]), "open": float(t["openPrice"]),
            "high": float(t["highPrice"]), "low": float(t["lowPrice"]), "qv": float(t["quoteVolume"])}


def icon_data(b):
    """Uygulamadaki sırayla: Binance logo CDN'i, yoksa CoinCap."""
    for url in ("https://bin.bnbstatic.com/static/assets/logos/%s.png" % urllib.parse.quote(b),
                "https://assets.coincap.io/assets/icons/%s@2x.png" % b.lower()):
        try:
            return "data:image/png;base64," + base64.b64encode(get(url, binary=True)).decode()
        except Exception:
            pass
    return None


def spark(symbol, interval, limit):
    """Uygulamadaki Sparks ile aynı: ilk mumun açılışı + kapanışlar."""
    k = get(REST + "klines?symbol=%s&interval=%s&limit=%d" % (symbol, interval, limit))
    return [float(k[0][1])] + [float(x[4]) for x in k]


def fetch_data():
    now = time.time() * 1000
    allt = get(REST + "ticker/24hr?type=MINI")
    # v8: delist edilmiş paritelerin donmuş ticker'ı (closeTime > 6 sa) sayılmaz, listelenmez
    live = [t for t in allt if t["symbol"].endswith("USDT") and len(t["symbol"]) > 4 and t["count"] > 0
            and t["symbol"][:-4] not in FIAT and t["symbol"][:-4] != "USDT" and now - t["closeTime"] <= 6 * 3600e3]
    pairs = len(live)
    up = down = flat = 0
    for t in live:
        o = float(t["openPrice"])
        if o <= 0:
            continue
        c = (float(t["lastPrice"]) - o) / o * 100
        if abs(c) < 0.005: flat += 1
        elif c > 0: up += 1
        else: down += 1
    by = {t["symbol"][:-4]: t for t in live}
    coins = [ticker_coin(by[b]) for b in BASES if b in by]
    liquid = [t for t in live if float(t["quoteVolume"]) >= 1e6 and float(t["openPrice"]) > 0]
    # Ana Sayfa: 24s yükselenler (ekran görüntüsünde çok düşük hacimli gürültü olmasın diye ≥ 1 Mn $)
    g24 = sorted(liquid, key=lambda t: -(float(t["lastPrice"]) / float(t["openPrice"])))[:5]
    gainers24 = [dict(ticker_coin(t), spark=spark(t["symbol"], "1h", 25)) for t in g24]
    # Piyasalar: 1s yükselenler, hacimce ilk 100 paritenin kayan penceresinden (uygulamadaki Market ile aynı uç nokta)
    top = [t["symbol"] for t in sorted(liquid, key=lambda t: -float(t["quoteVolume"])) if re.fullmatch(r"[A-Z0-9]+", t["symbol"])][:100]
    win = get(REST + "ticker?type=MINI&windowSize=1h&symbols=" + urllib.parse.quote(json.dumps(top, separators=(",", ":"))))
    hour = []
    for w in win:
        o = float(w["openPrice"])
        if o > 0 and w["closeTime"] - w["openTime"] >= 1800e3:
            c = ticker_coin(by[w["symbol"][:-4]])
            c["ch1h"] = (c["last"] - o) / o * 100
            hour.append(c)
    hour = sorted(hour, key=lambda c: -c["ch1h"])[:8]
    for c in hour:
        c["spark"] = spark(c["base"] + "USDT", "5m", 13)
    # Detay: BTC performans şeridi (5dk–7g pencere özeti, 30g saatlik mum açılışı)
    btc = by["BTC"]
    perf = {}
    for key, win_size in (("M5", "5m"), ("M15", "15m"), ("H1", "1h"), ("H4", "4h"), ("D7", "7d")):
        perf[key] = float(get(REST + "ticker?type=MINI&windowSize=%s&symbol=BTCUSDT" % win_size)["openPrice"])
    perf["D1"] = float(btc["openPrice"])
    perf["D30"] = float(get(REST + "klines?symbol=BTCUSDT&interval=1h&limit=1&startTime=%d" % (now - 30 * 86400e3))[0][1])
    kl = get(REST + "klines?symbol=BTCUSDT&interval=1h&limit=60")
    candles = [{"t": k[0], "o": float(k[1]), "h": float(k[2]), "l": float(k[3]), "c": float(k[4]), "v": float(k[5])} for k in kl]
    fx = get("https://open.er-api.com/v6/latest/USD")
    if fx.get("result") != "success":
        sys.exit("FX hatası: " + str(fx))
    icons = {}
    for b in set(BASES) | {c["base"] for c in gainers24 + hour}:
        data = icon_data(b)
        if data:
            icons[b] = data
    return {"coins": coins, "candles": candles, "pairs": pairs, "movement": [up, down, flat], "gainers24": gainers24,
            "hour": hour, "perf": perf, "fx": fx["rates"], "icons": icons, "time": int(now),
            "portfolio": PORTFOLIO, "holding": PORTFOLIO[0], "currencies": CURRENCIES}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="virgülle ayrılmış Play locale listesi")
    ap.add_argument("--shots", default="1,2,3,4,5,6")
    ap.add_argument("--devices", default="phone,tablet7,tablet10", help="phone, tablet7, tablet10")
    args = ap.parse_args()

    data = fetch_data()
    data["locales"] = {}
    for loc, (folder, tag, cur, lang, rtl) in LOCALES.items():
        data["locales"][loc] = {"strings": strings(folder), "tag": tag, "currency": cur, "languageName": lang,
                                "rtl": rtl, "captions": CAPTIONS[loc]}
    with open(os.path.join(HERE, "data.js"), "w") as fh:
        fh.write("window.DATA = " + json.dumps(data, ensure_ascii=False) + ";\n")

    locales = args.only.split(",") if args.only else list(LOCALES)
    shots = [int(s) for s in args.shots.split(",")]
    page = "file://" + os.path.join(HERE, "screenshots.html")
    # cihaz → (fastlane klasörü, URL eki, pencere boyutu, ölçek)
    devices = {"phone": ("phoneScreenshots", "", "1080,1920", "1"),
               "tablet7": ("sevenInchScreenshots", "&dev=tablet", "1920,1080", "1"),
               "tablet10": ("tenInchScreenshots", "&dev=tablet", "1920,1080", "1.3333333")}
    with tempfile.TemporaryDirectory() as tmp:
        for loc in locales:
            for dev in args.devices.split(","):
                folder, query, size, scale = devices[dev]
                out_dir = os.path.join(ROOT, "fastlane", "metadata", "android", loc, "images", folder)
                os.makedirs(out_dir, exist_ok=True)
                for s in shots:
                    png = os.path.join(tmp, "%s-%s-%d.png" % (loc, dev, s))
                    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                                    "--force-device-scale-factor=" + scale, "--window-size=" + size,
                                    "--virtual-time-budget=8000", "--screenshot=" + png,
                                    "%s?loc=%s&s=%d%s" % (page, loc, s, query)], check=True, capture_output=True)
                    jpg = os.path.join(out_dir, "%d.jpg" % s)
                    subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "88", png, "--out", jpg],
                                   check=True, capture_output=True)
                    print(loc, dev, s, os.path.getsize(jpg) // 1024, "KB")

if __name__ == "__main__":
    main()
