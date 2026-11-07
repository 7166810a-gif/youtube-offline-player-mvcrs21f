# חיפוש קולי ישיר ב-YouTube דרך ימות המשיח IVR2

גרסת MVP: המתקשר אומר בעברית מה הוא מחפש, ימות המשיח מתמללת את הדיבור ושולחת את הטקסט ל-Cloudflare Worker. ה-Worker מבצע חיפוש חי ב-YouTube Data API ומקריא בטלפון עד חמש כותרות וערוצים שנמצאו.

## מגבלה חשובה: חיפוש אינו השמעת וידאו

המערכת מחפשת ישירות ב-YouTube ומקריאה את התוצאות בטלפון. היא **אינה יכולה להזרים את האודיו של סרטוני YouTube לשיחת IVR או לחלץ ממנו MP3**. מדיניות YouTube אוסרת הורדה, הפרדת פס קול או השמעה לא מקוונת באמצעות ה-API. כדי לצפות/להאזין לסרטון, צריך לפתוח את קישור YouTube במכשיר מתאים, דרך נגן YouTube הרשמי.

אם יש לך קובץ אודיו שקיבלת מבעל הזכויות בנפרד, וניתנה לך הרשאה להשמיע אותו בטלפון, אפשר להשמיע אותו ממערכת ימות במסלול נפרד.

## מה כלול

- חיפוש חי ב-YouTube Data API (לא קטלוג מקומי).
- זיהוי דיבור בעברית של ימות המשיח.
- הקראת עד חמש תוצאות: כותרת וערוץ.
- שמירת מפתח YouTube כסוד ב-Cloudflare ולא בקוד.
- הגנה בסיסית עם סוד משותף בנתיב ה-API.

## 1. יצירת מפתח YouTube API

1. היכנס ל-[Google Cloud Console](https://console.cloud.google.com/).
2. צור פרויקט או בחר פרויקט קיים.
3. הפעל את **YouTube Data API v3**.
4. צור API key.
5. מומלץ להגביל את המפתח ל-YouTube Data API v3 ולשמור אותו פרטי.

לפי התיעוד הרשמי הנוכחי, ברירת המחדל היא 100 בקשות חיפוש `search.list` ביום לפרויקט. כל חיפוש מהמערכת צורך בקשת חיפוש אחת. אפשר לבדוק מכסה ולבקש הרחבה דרך Google Cloud Console.

## 2. פריסה ל-Cloudflare Workers

נדרש Node.js במחשב. שכפל את המאגר ופתח את תיקיית הפרויקט:

```bash
git clone https://github.com/7166810a-gif/youtube-offline-player-mvcrs21f.git
cd youtube-offline-player-mvcrs21f/yemot-ivr2
git checkout feature/yemot-ivr2-audio-search
npm install --save-dev wrangler
npx wrangler login
```

הגדר סוד אקראי לאימות מול ימות:

```bash
openssl rand -hex 32
npx wrangler secret put YEMOT_SHARED_SECRET
```

הדבק את הערך שנוצר כש-Wrangler מבקש אותו.

הוסף גם את מפתח YouTube:

```bash
npx wrangler secret put YOUTUBE_API_KEY
```

הדבק את ה-API key של Google. לעולם אל תכניס את המפתח לקוד או ל-GitHub.

בדיקות:

```bash
npm test
```

פריסה:

```bash
npx wrangler deploy
```

Cloudflare יציג כתובת בסגנון:

`https://yemot-ivr2-audio-search.<account>.workers.dev`

## 3. הגדרת שלוחת API בימות המשיח

הגדר בשלוחה:

```ini
type=api
api_link=https://yemot-ivr2-audio-search.<account>.workers.dev/yemot/<YOUR_SECRET>
api_say_tts=no
api_wait_answer_music_on_hold=yes
```

החלף את `<account>` ואת `<YOUR_SECRET>` בפרטים שלך.

השרת מבקש מהמתקשר חיפוש קולי באמצעות:

```
read=t-שלום, אמרו מה תרצו לחפש ביוטיוב=SEARCH,,voice
```

ימות מתמללת את הדיבור ושולחת לשרת את הפרמטר `SEARCH`. השרת מחפש ב-YouTube ומחזיר `id_list_message` עם עד חמש תוצאות להקראה.

כדי לחזור לשלוחה ולבצע חיפוש נוסף אחרי הקראת התוצאות, אפשר להגדיר בהגדרות השלוחה:

```ini
api_end_goto=/X
```

החלף `/X` בנתיב של שלוחת החיפוש עצמה, ובדוק את ההתנהגות בשיחת ניסיון לפני פתיחה לציבור.

## 4. עלויות

- Cloudflare Workers: אפשר להתחיל במסלול החינמי, בכפוף למכסות העדכניות.
- YouTube Data API: ללא חיוב רגיל במסגרת המכסה, אבל ברירת המחדל מוגבלת ל-100 חיפושים ביום.
- ימות המשיח: מודול API מתועד כחינמי כרגע, אך הדבר עשוי להשתנות.
- שיחות טלפון, מספר גישה ודקות שיחה עשויים להיות בתשלום אצל ימות או ספק התקשורת. לכן לא ניתן להבטיח אפס עלות כוללת.

## 5. פרטיות ואבטחה

- אל תפרסם את כתובת ה-API עם הסוד.
- הגבל את מפתח YouTube ל-YouTube Data API בלבד.
- אל תשמור API key בקובצי Git.
- ה-Worker אינו שומר את מילות החיפוש או את התוצאות.
- כותרות וערוצים מוצגים לצורך חיפוש ומיוחסים ל-YouTube; אין להציג אותם כאילו נוצרו על ידי השירות.

## תיעוד רשמי

- [YouTube Data API — Search: list](https://developers.google.com/youtube/data/reference/rest/v3/search/list)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube API Services Policies Guide](https://developers.google.com/youtube/terms/developer-policies-guide)
- [מודול API של ימות המשיח](https://f2.freeivr.co.il/topic/56/מודול-api-תקשור-עם-מחשבים-וממשקי-נתונים-חיצוניים)
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [Cloudflare Workers secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
