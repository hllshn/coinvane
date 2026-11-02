"""Play Store metinleri (11 dil, fastlane supply biçimi). Kullanım (repo kökünden): python3 design/store/listings.py"""
import os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
M = {
 "en-US": ("Coinvane: Crypto Price Tracker",
  "Live prices for 700+ coins in your currency, real charts, alerts and portfolio.",
  """Coinvane shows where the crypto market is heading — live, in your own currency and language.

• Live prices for 700+ coins, updated every second
• Real candlestick and line charts with volume: 15m, 1H, 4H, 1D, 1W
• Price alerts that fire instantly while the app is open and in the background when it is closed
• Portfolio: track holdings, total balance, 24h change and profit/loss
• Home screen widget with your favorite coins
• 25 currencies (USD, EUR, GBP, TRY, JPY, KRW, BRL, INR…) and 11 languages
• Favorites, gainers and losers, fast search
• Light and dark theme, Material You colors

No account, no ads, no tracking. Your favorites, alerts and portfolio stay on your device.

Market data: Binance public API · Exchange rates: ExchangeRate-API.
Prices are for information only and are not investment advice."""),
 "tr-TR": ("Coinvane: Kripto Fiyat Takibi",
  "700+ coinin canlı fiyatı kendi para biriminizde; gerçek grafik, alarm, portföy.",
  """Coinvane kripto piyasasının yönünü gösterir — canlı, kendi para biriminiz ve dilinizde.

• 700'den fazla coinin saniye saniye güncellenen canlı fiyatı
• Hacimli gerçek mum ve çizgi grafikleri: 15dk, 1s, 4s, 1G, 1H
• Uygulama açıkken anında, kapalıyken arka planda çalışan fiyat alarmları
• Portföy: varlıklarınızı, toplam bakiyeyi, 24 saatlik değişimi ve kâr/zararı izleyin
• Favori coinlerinizi gösteren ana ekran widget'ı
• 25 para birimi (TRY, USD, EUR, GBP, JPY…) ve 11 dil
• Favoriler, yükselenler/düşenler, hızlı arama
• Açık ve koyu tema, Material You renkleri

Hesap yok, reklam yok, takip yok. Favorileriniz, alarmlarınız ve portföyünüz cihazınızda kalır.

Piyasa verisi: Binance public API · Döviz kurları: ExchangeRate-API.
Fiyatlar bilgi amaçlıdır, yatırım tavsiyesi değildir."""),
 "de-DE": ("Coinvane: Krypto-Kurs-Tracker",
  "Live-Kurse für 700+ Coins in deiner Währung, echte Charts, Alarme, Portfolio.",
  """Coinvane zeigt, wohin sich der Kryptomarkt bewegt – live, in deiner Währung und Sprache.

• Live-Kurse für über 700 Coins, sekündlich aktualisiert
• Echte Kerzen- und Liniencharts mit Volumen: 15m, 1H, 4H, 1T, 1W
• Kursalarme – sofort bei geöffneter App, im Hintergrund wenn sie geschlossen ist
• Portfolio: Bestände, Gesamtsaldo, 24h-Änderung sowie Gewinn und Verlust
• Homescreen-Widget mit deinen Lieblingscoins
• 25 Währungen und 11 Sprachen
• Favoriten, Gewinner und Verlierer, schnelle Suche
• Helles und dunkles Design, Material-You-Farben

Kein Konto, keine Werbung, kein Tracking. Favoriten, Alarme und Portfolio bleiben auf deinem Gerät.

Marktdaten: Binance Public API · Wechselkurse: ExchangeRate-API.
Kurse dienen nur zur Information und sind keine Anlageberatung."""),
 "fr-FR": ("Coinvane: Suivi des cryptos",
  "700+ cryptos en direct dans votre devise : graphiques, alertes et portefeuille.",
  """Coinvane montre la direction du marché crypto — en direct, dans votre devise et votre langue.

• Cours en direct de plus de 700 cryptos, actualisés chaque seconde
• Vrais graphiques en chandeliers et en ligne avec volume : 15 min, 1 h, 4 h, 1 J, 1 S
• Alertes de prix : immédiates quand l’app est ouverte, en arrière-plan sinon
• Portefeuille : positions, solde total, variation sur 24 h, plus/moins-values
• Widget d’écran d’accueil avec vos cryptos favorites
• 25 devises et 11 langues
• Favoris, hausses et baisses, recherche rapide
• Thème clair et sombre, couleurs Material You

Pas de compte, pas de pub, pas de pistage. Vos données restent sur votre appareil.

Données de marché : API publique Binance · Taux de change : ExchangeRate-API.
Les cours sont fournis à titre indicatif et ne constituent pas un conseil en investissement."""),
 "es-ES": ("Coinvane: Precios cripto",
  "Precios en vivo de 700+ monedas en tu divisa: gráficos, alertas y cartera.",
  """Coinvane muestra hacia dónde va el mercado cripto: en vivo, en tu moneda y tu idioma.

• Precios en vivo de más de 700 monedas, actualizados cada segundo
• Gráficos reales de velas y líneas con volumen: 15m, 1H, 4H, 1D, 1S
• Alertas de precio: al instante con la app abierta y en segundo plano cuando está cerrada
• Cartera: posiciones, saldo total, cambio en 24 h y ganancias/pérdidas
• Widget de pantalla de inicio con tus monedas favoritas
• 25 divisas y 11 idiomas
• Favoritos, al alza y a la baja, búsqueda rápida
• Tema claro y oscuro, colores Material You

Sin cuenta, sin anuncios, sin rastreo. Tus datos se quedan en tu dispositivo.

Datos de mercado: API pública de Binance · Tipos de cambio: ExchangeRate-API.
Los precios son solo informativos y no constituyen asesoramiento de inversión."""),
 "pt-BR": ("Coinvane: Cotações de cripto",
  "Preços ao vivo de 700+ moedas na sua moeda, gráficos reais, alertas e carteira.",
  """O Coinvane mostra para onde o mercado cripto está indo — ao vivo, na sua moeda e no seu idioma.

• Preços ao vivo de mais de 700 moedas, atualizados a cada segundo
• Gráficos reais de candles e linha com volume: 15m, 1H, 4H, 1D, 1S
• Alertas de preço: na hora com o app aberto e em segundo plano quando fechado
• Carteira: posições, saldo total, variação em 24h e lucro/prejuízo
• Widget de tela inicial com suas moedas favoritas
• 25 moedas fiduciárias e 11 idiomas
• Favoritas, em alta e em baixa, busca rápida
• Tema claro e escuro, cores Material You

Sem conta, sem anúncios, sem rastreamento. Seus dados ficam no seu aparelho.

Dados de mercado: API pública da Binance · Câmbio: ExchangeRate-API.
Os preços são apenas informativos e não constituem recomendação de investimento."""),
 "ru-RU": ("Coinvane: курсы криптовалют",
  "Курсы 700+ монет в вашей валюте, настоящие графики, оповещения и портфель.",
  """Coinvane показывает, куда движется криптовалютный рынок — в реальном времени, в вашей валюте и на вашем языке.

• Курсы более 700 монет с обновлением каждую секунду
• Настоящие свечные и линейные графики с объёмом: 15м, 1ч, 4ч, 1Д, 1Н
• Ценовые оповещения: мгновенно в открытом приложении и в фоне, когда оно закрыто
• Портфель: позиции, общий баланс, изменение за 24 ч, прибыль и убытки
• Виджет с избранными монетами
• 25 валют и 11 языков
• Избранное, лидеры роста и падения, быстрый поиск
• Светлая и тёмная тема, цвета Material You

Без аккаунта, рекламы и слежки. Ваши данные остаются на устройстве.

Рыночные данные: публичный API Binance · Курсы валют: ExchangeRate-API.
Цены носят информационный характер и не являются инвестиционной рекомендацией."""),
 "ar": ("Coinvane: أسعار الكريبتو",
  "أسعار مباشرة لأكثر من 700 عملة بعملتك، رسوم بيانية حقيقية، تنبيهات ومحفظة.",
  """يُظهر Coinvane اتجاه سوق العملات الرقمية — مباشرةً وبعملتك ولغتك.

• أسعار مباشرة لأكثر من 700 عملة تُحدَّث كل ثانية
• رسوم بيانية حقيقية بالشموع والخطوط مع الحجم: 15د، 1س، 4س، 1ي، 1أ
• تنبيهات أسعار فورية عند فتح التطبيق وفي الخلفية عند إغلاقه
• المحفظة: حيازاتك وإجمالي الرصيد وتغيّر 24 ساعة والربح/الخسارة
• أداة للشاشة الرئيسية بعملاتك المفضلة
• 25 عملة و11 لغة
• المفضلة والأكثر ارتفاعًا وانخفاضًا وبحث سريع
• مظهر فاتح وداكن وألوان Material You

بلا حساب ولا إعلانات ولا تتبّع. تبقى بياناتك على جهازك.

بيانات السوق: واجهة Binance العامة · أسعار الصرف: ExchangeRate-API.
الأسعار للعلم فقط وليست نصيحة استثمارية."""),
 "ja-JP": ("Coinvane: 暗号資産の価格チャート",
  "700以上のコインをあなたの通貨でリアルタイム表示。チャート、アラート、ポートフォリオ。",
  """Coinvane は暗号資産市場の向かう先を、あなたの通貨と言語でリアルタイムに表示します。

• 700以上のコインの価格を毎秒更新
• 出来高付きのローソク足・ラインチャート：15分、1時間、4時間、1日、1週
• 価格アラート：アプリ使用中は即時、閉じているときもバックグラウンドで通知
• ポートフォリオ：保有数量、合計残高、24時間の変動、損益
• お気に入りコインを表示するホーム画面ウィジェット
• 25通貨・11言語に対応
• お気に入り、上昇・下落ランキング、高速検索
• ライト/ダークテーマ、Material You カラー

アカウント不要、広告なし、トラッキングなし。データはすべて端末内に保存されます。

市場データ：Binance 公開 API · 為替レート：ExchangeRate-API。
価格は情報提供のみを目的としており、投資助言ではありません。"""),
 "ko-KR": ("Coinvane: 코인 시세 추적",
  "700개 이상 코인의 실시간 시세를 내 통화로. 차트, 가격 알림, 포트폴리오.",
  """Coinvane은 암호화폐 시장의 방향을 내 통화와 언어로 실시간 보여 줍니다.

• 700개 이상 코인의 시세를 매초 업데이트
• 거래량이 포함된 캔들·라인 차트: 15분, 1시간, 4시간, 1일, 1주
• 가격 알림: 앱 사용 중에는 즉시, 닫혀 있을 때는 백그라운드에서 알림
• 포트폴리오: 보유 수량, 총 잔액, 24시간 변동, 손익
• 즐겨찾는 코인을 보여 주는 홈 화면 위젯
• 25개 통화, 11개 언어 지원
• 즐겨찾기, 상승·하락 순위, 빠른 검색
• 라이트/다크 테마, Material You 색상

계정·광고·추적 없음. 모든 데이터는 기기에만 저장됩니다.

시장 데이터: Binance 공개 API · 환율: ExchangeRate-API.
가격은 참고용이며 투자 조언이 아닙니다."""),
 "zh-CN": ("Coinvane: 加密货币行情",
  "700+ 币种实时行情，以您的货币计价；真实K线、价格提醒与资产组合。",
  """Coinvane 以您的货币和语言，实时呈现加密市场的走向。

• 700 多个币种实时行情，每秒更新
• 带成交量的真实 K 线与折线图：15分、1小时、4小时、1日、1周
• 价格提醒：应用打开时即时提醒，关闭时在后台提醒
• 资产组合：持仓、总资产、24 小时变化与盈亏
• 显示自选币种的桌面小部件
• 支持 25 种货币与 11 种语言
• 自选、涨跌幅榜、快速搜索
• 浅色/深色主题，Material You 动态配色

无需注册、无广告、无追踪。您的数据只保存在本机。

行情数据：Binance 公开 API · 汇率：ExchangeRate-API。
价格仅供参考，不构成投资建议。"""),
}
CHANGELOG = {
    "en-US": "Coinvane is here: 700+ coins, 25 currencies, 11 languages, portfolio, widget and instant price alerts.",
    "tr-TR": "Kripto Para artık Coinvane: 700+ coin, 25 para birimi, 11 dil, portföy, widget ve anlık fiyat alarmları.",
    "de-DE": "Willkommen bei Coinvane: 700+ Coins, 25 Währungen, 11 Sprachen, Portfolio, Widget und sofortige Kursalarme.",
    "fr-FR": "Voici Coinvane : 700+ cryptos, 25 devises, 11 langues, portefeuille, widget et alertes de prix instantanées.",
    "es-ES": "Llega Coinvane: 700+ monedas, 25 divisas, 11 idiomas, cartera, widget y alertas de precio al instante.",
    "pt-BR": "Chegou o Coinvane: 700+ moedas, 25 moedas fiduciárias, 11 idiomas, carteira, widget e alertas na hora.",
    "ru-RU": "Встречайте Coinvane: 700+ монет, 25 валют, 11 языков, портфель, виджет и мгновенные оповещения о ценах.",
    "ar": "مرحبًا بـ Coinvane: أكثر من 700 عملة و25 عملة محلية و11 لغة ومحفظة وأداة للشاشة الرئيسية وتنبيهات فورية.",
    "ja-JP": "Coinvane 登場：700以上のコイン、25通貨、11言語、ポートフォリオ、ウィジェット、即時価格アラート。",
    "ko-KR": "Coinvane 출시: 700개 이상 코인, 25개 통화, 11개 언어, 포트폴리오, 위젯, 즉시 가격 알림.",
    "zh-CN": "Coinvane 来了：700+ 币种、25 种货币、11 种语言、资产组合、桌面小部件与即时价格提醒。",
}
for loc, (title, short, full) in M.items():
    assert len(title) <= 30, (loc, len(title))
    assert len(short) <= 80, (loc, len(short))
    assert len(full) <= 4000, loc
    d = os.path.join(ROOT, "fastlane/metadata/android", loc)
    os.makedirs(os.path.join(d, "changelogs"), exist_ok=True)
    for name, text in (("title", title), ("short_description", short), ("full_description", full)):
        open(os.path.join(d, name + ".txt"), "w").write(text + "\n")
    assert len(CHANGELOG[loc]) <= 500, loc
    open(os.path.join(d, "changelogs", "24.txt"), "w").write(CHANGELOG[loc] + "\n")
print("ok", len(M))

# --- Tanıtım görseli (1024×500), her dilin kendi sloganıyla. Gerekli: rsvg-convert (brew install librsvg) ---
import subprocess
TAGLINES = {"en-US": "Read the market’s direction.", "tr-TR": "Piyasanın yönünü oku.",
            "de-DE": "Lies die Richtung des Marktes.", "fr-FR": "Lisez la direction du marché.",
            "es-ES": "Lee la dirección del mercado.", "pt-BR": "Leia a direção do mercado.",
            "ru-RU": "Читайте направление рынка.", "ar": "اقرأ اتجاه السوق", "ja-JP": "市場の方向を読む。",
            "ko-KR": "시장의 방향을 읽다.", "zh-CN": "洞察市场方向。"}
template = open(os.path.join(ROOT, "design", "coinvane-feature-graphic.svg")).read()
assert "Read the market’s direction." in template
for loc, tag in TAGLINES.items():
    svg = template.replace("Read the market’s direction.", tag)
    out = os.path.join(ROOT, "fastlane/metadata/android", loc, "images")
    os.makedirs(out, exist_ok=True)
    subprocess.run(["rsvg-convert", "-w", "1024", "-h", "500", "-o", os.path.join(out, "featureGraphic.png")],
                   input=svg.encode(), check=True)
print("feature graphics ok", len(TAGLINES))
