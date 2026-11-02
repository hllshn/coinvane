#!/usr/bin/env python3
"""Coinvane Play Store ekran görüntülerini üretir (11 dil × 6 ekran, 1080×1920 JPEG).

Kullanım (repo kökünden):  python3 design/store/render.py [--only en-US,tr-TR] [--shots 1,3]

1. Binance'ten anlık fiyatları ve BTC 1 saatlik mumlarını, ExchangeRate-API'den kurları çeker.
2. Uygulamanın kendi metinlerini app/src/main/res/values*/strings.xml dosyalarından okur.
3. design/store/data.js'i yazar ve screenshots.html'i her dil/ekran için headless Chrome ile fotoğraflar.
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

# Ekran başlıkları: (başlık, alt satır) × 6 — liste, grafik, alarm, portföy, widget, ayarlar
CAPTIONS = {
    "en-US": [("Every coin, live", "700+ cryptocurrencies, updated every second."),
              ("Real charts", "Candles and volume from 15 minutes to a week."),
              ("Never miss a move", "Price alerts that fire the moment your target is hit."),
              ("Your portfolio at a glance", "Total balance, 24h change and profit/loss."),
              ("Prices on your home screen", "A widget for your favorite coins."),
              ("Your currency, your language", "25 currencies and 11 languages.")],
    "tr-TR": [("Tüm coinler, canlı", "700'den fazla kripto para, saniye saniye güncel."),
              ("Gerçek grafikler", "15 dakikadan 1 haftaya mum ve hacim grafikleri."),
              ("Hiçbir hareketi kaçırma", "Hedef fiyata ulaşıldığı an çalan fiyat alarmları."),
              ("Portföyün tek bakışta", "Toplam bakiye, 24 saatlik değişim ve kâr/zarar."),
              ("Fiyatlar ana ekranında", "Favori coinlerin için bir widget."),
              ("Senin paran, senin dilin", "25 para birimi ve 11 dil.")],
    "de-DE": [("Jeder Coin, live", "Über 700 Kryptowährungen, sekündlich aktualisiert."),
              ("Echte Charts", "Kerzen und Volumen von 15 Minuten bis zu einer Woche."),
              ("Keine Bewegung verpassen", "Kursalarme, sobald dein Ziel erreicht ist."),
              ("Dein Portfolio im Blick", "Gesamtsaldo, 24h-Änderung sowie Gewinn und Verlust."),
              ("Kurse auf dem Homescreen", "Ein Widget für deine Lieblingscoins."),
              ("Deine Währung, deine Sprache", "25 Währungen und 11 Sprachen.")],
    "fr-FR": [("Toutes les cryptos, en direct", "Plus de 700 cryptomonnaies, actualisées chaque seconde."),
              ("De vrais graphiques", "Chandeliers et volume, de 15 minutes à une semaine."),
              ("Ne ratez aucun mouvement", "Des alertes dès que votre prix cible est atteint."),
              ("Votre portefeuille d’un coup d’œil", "Solde total, variation sur 24 h et plus/moins-values."),
              ("Les cours sur votre écran d’accueil", "Un widget pour vos cryptos favorites."),
              ("Votre devise, votre langue", "25 devises et 11 langues.")],
    "es-ES": [("Todas las monedas, en vivo", "Más de 700 criptomonedas, actualizadas cada segundo."),
              ("Gráficos de verdad", "Velas y volumen de 15 minutos a una semana."),
              ("No te pierdas ningún movimiento", "Alertas al instante cuando se alcanza tu precio."),
              ("Tu cartera de un vistazo", "Saldo total, cambio en 24 h y ganancias/pérdidas."),
              ("Precios en tu pantalla de inicio", "Un widget para tus monedas favoritas."),
              ("Tu moneda, tu idioma", "25 divisas y 11 idiomas.")],
    "pt-BR": [("Todas as moedas, ao vivo", "Mais de 700 criptomoedas, atualizadas a cada segundo."),
              ("Gráficos de verdade", "Candles e volume de 15 minutos a uma semana."),
              ("Não perca nenhum movimento", "Alertas na hora em que seu preço-alvo é atingido."),
              ("Sua carteira num relance", "Saldo total, variação em 24h e lucro/prejuízo."),
              ("Preços na tela inicial", "Um widget para suas moedas favoritas."),
              ("Sua moeda, seu idioma", "25 moedas e 11 idiomas.")],
    "ru-RU": [("Все монеты в реальном времени", "Более 700 криптовалют с обновлением каждую секунду."),
              ("Настоящие графики", "Свечи и объём — от 15 минут до недели."),
              ("Не пропустите движение", "Оповещение в момент, когда цена достигнет цели."),
              ("Портфель с первого взгляда", "Общий баланс, изменение за 24 ч, прибыль и убытки."),
              ("Курсы на главном экране", "Виджет с избранными монетами."),
              ("Ваша валюта, ваш язык", "25 валют и 11 языков.")],
    "ar": [("كل العملات، مباشرةً", "أكثر من 700 عملة رقمية تُحدَّث كل ثانية."),
           ("رسوم بيانية حقيقية", "شموع وحجم تداول من 15 دقيقة إلى أسبوع."),
           ("لا تفوّت أي حركة", "تنبيهات فورية لحظة وصول السعر إلى هدفك."),
           ("محفظتك في لمحة", "إجمالي الرصيد وتغيّر 24 ساعة والربح والخسارة."),
           ("الأسعار على شاشتك الرئيسية", "أداة لعملاتك المفضلة."),
           ("عملتك ولغتك", "25 عملة و11 لغة.")],
    "ja-JP": [("すべてのコインをリアルタイムで", "700以上の暗号資産を毎秒更新。"),
              ("本格チャート", "15分足から週足まで、ローソク足と出来高。"),
              ("値動きを逃さない", "目標価格に達した瞬間に届く価格アラート。"),
              ("資産をひと目で", "合計残高、24時間の変動、損益。"),
              ("ホーム画面に価格を", "お気に入りコインのウィジェット。"),
              ("あなたの通貨と言語で", "25通貨・11言語に対応。")],
    "ko-KR": [("모든 코인을 실시간으로", "700개 이상의 암호화폐를 매초 업데이트."),
              ("진짜 차트", "15분봉부터 주봉까지, 캔들과 거래량."),
              ("움직임을 놓치지 마세요", "목표 가격에 도달하는 순간 알려 드립니다."),
              ("포트폴리오를 한눈에", "총 잔액, 24시간 변동, 손익."),
              ("홈 화면에서 시세 확인", "즐겨찾는 코인 위젯."),
              ("내 통화, 내 언어로", "25개 통화, 11개 언어.")],
    "zh-CN": [("全部币种，实时行情", "700 多个加密货币，每秒更新。"),
              ("专业图表", "从 15 分钟到 1 周的 K 线与成交量。"),
              ("不错过任何波动", "价格到达目标的那一刻即提醒。"),
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


def fetch_data():
    symbols = json.dumps([b + "USDT" for b in BASES], separators=(",", ":"))
    tick = get(REST + "ticker/24hr?type=MINI&symbols=" + urllib.parse.quote(symbols))
    coins = [{"base": t["symbol"][:-4], "name": NAMES[t["symbol"][:-4]], "last": float(t["lastPrice"]),
              "open": float(t["openPrice"]), "high": float(t["highPrice"]), "low": float(t["lowPrice"]),
              "qv": float(t["quoteVolume"])} for t in tick]
    kl = get(REST + "klines?symbol=BTCUSDT&interval=1h&limit=60")
    candles = [{"t": k[0], "o": float(k[1]), "h": float(k[2]), "l": float(k[3]), "c": float(k[4]), "v": float(k[5])} for k in kl]
    allt = get(REST + "ticker/24hr?type=MINI")
    pairs = sum(1 for t in allt if t["symbol"].endswith("USDT") and len(t["symbol"]) > 4 and t["count"] > 0
                and t["symbol"][:-4] not in FIAT and t["symbol"][:-4] != "USDT")
    fx = get("https://open.er-api.com/v6/latest/USD")
    if fx.get("result") != "success":
        sys.exit("FX hatası: " + str(fx))
    icons = {}
    for b in BASES:
        png = get("https://assets.coincap.io/assets/icons/%s@2x.png" % b.lower(), binary=True)
        icons[b] = "data:image/png;base64," + base64.b64encode(png).decode()
    return {"coins": coins, "candles": candles, "pairs": pairs, "fx": fx["rates"], "icons": icons,
            "time": int(time.time() * 1000), "portfolio": PORTFOLIO, "holding": PORTFOLIO[0], "currencies": CURRENCIES}


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
