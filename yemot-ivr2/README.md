# חיפוש קולי ב-YouTube דרך ימות המשיח IVR2

המערכת מאפשרת למתקשר לחפש סרטונים ב-YouTube באמצעות דיבור בעברית, ולקבל הקראה טלפונית של עד חמש כותרות תוצאות.

## מגבלה חשובה — השמעת אודיו

המערכת אינה יכולה להשמיע בתוך שיחת הטלפון את פס הקול של סרטוני YouTube. YouTube Data API מחזיר מטא-נתונים וקישורים, לא זרם אודיו להזרמה בטלפון. מדיניות YouTube אוסרת להוריד או להפריד את פס הקול של סרטוני YouTube או לאפשר השמעת רקע מחוץ לנגן הרשמי.

לכן הגרסה הזאת מחפשת ומקריאה כותרות בלבד. כדי להאזין לסרטון עצמו, המאזין צריך לפתוח אותו בנגן YouTube הרשמי, למשל בטלפון חכם.

אם נדרשת השמעה ממשית בתוך שיחת הטלפון, צריך לבנות מערכת נפרדת של חיפוש והשמעת קובצי אודיו שהועלו על ידך ושיש לך הרשאה מפורשת להפיץ ולהשמיע אותם. היא לא תהיה נגן אודיו של תוצאות YouTube.

## מהלך השיחה

1. המתקשר מקיש 1 להסכמה לשימוש בחיפוש.
2. אומר בעברית את נושא החיפוש.
3. השרת מחפש עד חמש תוצאות ב-YouTube Data API.
4. ימות מקריאה את שמות הסרטונים.
5. המתקשר יכול לבקש חיפוש נוסף.

## 1. יצירת מפתח YouTube API

1. פתח פרויקט ב-Google Cloud Console.
2. הפעל YouTube Data API v3.
3. צור API key והגבל אותו ל-YouTube Data API v3.
4. אל תעלה את המפתח ל-GitHub ואל תשלח אותו בצ'אט.

מכסת ברירת המחדל של `search.list` היא 100 קריאות ביום לפרויקט. כל קריאת חיפוש עולה יחידת מכסה אחת. ניתן לבקש הרחבת מכסה לאחר ביקורת תאימות של YouTube.

## 2. פריסת Cloudflare Workers

נדרש Node.js במחשב:

```bash
git clone https://github.com/7166810a-gif/youtube-offline-player-mvcrs21f.git
cd youtube-offline-player-mvcrs21f/yemot-ivr2
git checkout feature/yemot-ivr2-audio-search
npm install --save-dev wrangler
npx wrangler login
```

צור סוד לכתובת ה-IVR:

```bash
openssl rand -hex 32
npx wrangler secret put YEMOT_SHARED_SECRET
```

הדבק את הסוד כאשר Wrangler מבקש ערך. לאחר מכן שמור את מפתח YouTube:

```bash
npx wrangler secret put YOUTUBE_API_KEY
```

בדיקות ופריסה:

```bash
npm test
npm run deploy
```

Cloudflare יציג כתובת בסגנון:

`https://yemot-youtube-search.<account>.workers.dev`

כתובת API לימות:

`https://yemot-youtube-search.<account>.workers.dev/yemot/<YOUR_SHARED_SECRET>`

דפי פרטיות ותנאי שימוש יהיו זמינים בנתיבים `/privacy` ו-`/terms`; בדיקת תקינות ב-`/health`.

## 3. הגדרת שלוחת API בימות המשיח

בשלוחה ייעודית:

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

החלף את `<account>` ואת `<YOUR_SHARED_SECRET>` בפרטים שלך. מודול ה-API משתמש ב-`read` עם `voice` כדי לקלוט את החיפוש הקולי בעברית, וב-`id_list_message` כדי להקריא את הכותרות.

## 4. עלויות ומגבלות

- Cloudflare Workers Free: עד 100,000 בקשות ביום, בכפוף למגבלות המסלול.
- YouTube Data API: 100 בקשות `search.list` ביום כברירת מחדל.
- מודול API של ימות תואר כחינמי לעת עתה, אך התנאים עשויים להשתנות.
- דקות שיחה, מספרי גישה ושירותי תקשורת עשויים להיות בתשלום.

אין הבטחה לאפס עלות כוללת.

## תיעוד רשמי

- [YouTube Data API Search: list](https://developers.google.com/youtube/data/reference/rest/v3/search/list)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube quota calculator](https://developers.google.com/youtube/data/determine_quota_cost)
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [מודול API של ימות המשיח](https://f2.freeivr.co.il/topic/56/מודול-api-תקשור-עם-מחשבים-וממשקי-נתונים-חיצוניים)
