# חיפוש קולי ישיר ב-YouTube דרך ימות המשיח IVR2

גרסת MVP זו אינה משתמשת בקטלוג מקומי. המתקשר אומר נושא בעברית, והשרת מחפש בזמן אמת ב-YouTube באמצעות YouTube Data API ומקריא עד חמש כותרות תוצאות דרך ימות המשיח.

**חשוב:** ה-IVR יכול לחפש ולהקריא כותרות, אך אינו יכול להזרים לשיחת הטלפון את פס הקול של סרטון YouTube. תנאי YouTube אוסרים הורדת סרטונים, הפרדת פס קול, הצעת MP3 או השמעת רקע במקום נגן YouTube. כדי לצפות/להאזין לסרטון יש לפתוח אותו בנגן YouTube הרשמי.

## מה כלול

- חיפוש ישיר בזמן אמת ב-YouTube Data API.
- זיהוי דיבור בעברית דרך `read` מסוג `voice` של ימות המשיח.
- הקראת עד 5 כותרות תוצאות לפי סדר הרלוונטיות של YouTube.
- אפשרות לבצע חיפוש נוסף באותה שיחה.
- הסכמה לפני שימוש, דפי תנאי שימוש ומדיניות פרטיות.
- הגנה על endpoint הטלפוני באמצעות סוד משותף.
- בדיקות אוטומטיות עם תגובת YouTube מדומה.

## 1. יצירת מפתח YouTube API

1. פתח פרויקט ב-[Google Cloud Console](https://console.cloud.google.com/).
2. הפעל בפרויקט את **YouTube Data API v3**.
3. היכנס ל-Credentials וצור API key.
4. הגבל את המפתח ל-YouTube Data API v3, ושמור אותו בסוד. אל תכניס אותו ל-GitHub.

לפי התיעוד הרשמי, `search.list` מוגבל כברירת מחדל ל-100 קריאות ביום לפרויקט; כל קריאת חיפוש עולה יחידת מכסה אחת. המכסה אינה זהה למכסת 10,000 היחידות של פעולות API אחרות. ניתן לבקש הרחבת מכסה באמצעות תהליך ביקורת של YouTube.

## 2. פריסה ל-Cloudflare Workers

נדרש Node.js במחשב. שכפל את המאגר ופתח את תיקיית הפרויקט:

```bash
git clone https://github.com/7166810a-gif/youtube-offline-player-mvcrs21f.git
cd youtube-offline-player-mvcrs21f/yemot-ivr2
git checkout feature/yemot-ivr2-audio-search
npm install --save-dev wrangler
npx wrangler login
```

הגדר סוד משותף לימות:

```bash
openssl rand -hex 32
npx wrangler secret put YEMOT_SHARED_SECRET
```

הדבק את הסוד שנוצר כאשר Wrangler מבקש ערך. הגדר גם את מפתח YouTube:

```bash
npx wrangler secret put YOUTUBE_API_KEY
```

בדיקות:

```bash
npm test
```

פריסה:

```bash
npm run deploy
```

Cloudflare יציג כתובת כמו:

`https://yemot-youtube-search.<account>.workers.dev`

כתובת API לימות:

`https://yemot-youtube-search.<account>.workers.dev/yemot/<YOUR_SHARED_SECRET>`

דפי מדיניות ציבוריים:

- `https://yemot-youtube-search.<account>.workers.dev/privacy`
- `https://yemot-youtube-search.<account>.workers.dev/terms`

בדיקת שרת:

`https://yemot-youtube-search.<account>.workers.dev/health`

## 3. הגדרות שלוחת API בימות המשיח

הגדר בשלוחה:

```ini
type=api
api_link=https://yemot-youtube-search.<account>.workers.dev/yemot/<YOUR_SHARED_SECRET>
api_say_tts=no
api_wait_answer_music_on_hold=yes
api_phone_send=no
api_did_send=no
api_call_id_send=no
api_extension_send=no
api_enter_id_send=no
api_enter_id_name_send=no
api_time_send=no
```

החלף את `<account>` ואת `<YOUR_SHARED_SECRET>` בפרטים שלך. שלח POST או GET כברירת מחדל; אין צורך להגדיר מראש פרמטרי חיפוש ב-`api_000`, כי השרת מפעיל דיאלוג דינמי בעזרת `read`.

## 4. מהלך השיחה

1. השרת מבקש הסכמה לשימוש בחיפוש YouTube ובתנאי השימוש/מדיניות הפרטיות.
2. המתקשר מקיש 1 להסכמה.
3. המערכת מבקשת לומר את נושא החיפוש בעברית.
4. השרת קורא ל-YouTube ומקריא עד חמש כותרות.
5. השרת מבקש חיפוש נוסף; אפשר לבצע עד 10 חיפושים בשיחה.

## 5. עלויות ומגבלות

- Cloudflare Workers Free מאפשר עד 100,000 בקשות Worker ביום לפי מגבלת Cloudflare הנוכחית.
- YouTube Data API מספק כברירת מחדל 100 קריאות `search.list` ביום לפרויקט.
- מודול API בימות המשיח תואר בפורום הרשמי כחינמי לעת עתה, אך ימות מציינת שהוא עשוי להפוך לשירות בתשלום.
- שיחות טלפון ודקות תקשורת עשויות להיות בתשלום אצל ימות או ספק הטלפוניה. לכן אי אפשר להבטיח מערכת ללא עלות כוללת.
- כשהמכסה היומית של YouTube תסתיים, החיפוש יפסיק לעבוד עד איפוס המכסה או הרחבתה.

## 6. פרטיות ותנאי שימוש

השרת אינו שומר חיפושים או תוצאות. מילות החיפוש נשלחות ל-Google/YouTube לצורך החיפוש. דפי `/privacy` ו-`/terms` זמינים אחרי הפריסה. יש לבדוק ולעדכן את פרטי בעל השירות ואת מדיניות הפרטיות לפני פתיחה לציבור.

לפי מדיניות YouTube API, אין להוריד סרטונים, להפריד אודיו/וידאו, להציע MP3 או לאפשר השמעת רקע. השירות הזה מבצע חיפוש ומקריא כותרות בלבד; הוא אינו מנגן את תוכן הסרטון.

## תיעוד רשמי

- [YouTube Data API — Search: list](https://developers.google.com/youtube/data/reference/rest/v3/search/list)
- [YouTube Data API — Quota calculator](https://developers.google.com/youtube/data/determine_quota_cost)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [מודול API של ימות המשיח](https://f2.freeivr.co.il/topic/56/מודול-api-תקשור-עם-מחשבים-וממשקי-נתונים-חיצוניים)
