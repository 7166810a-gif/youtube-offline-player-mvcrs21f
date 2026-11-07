# חיפוש קולי והשמעת קובצי אודיו — ימות המשיח IVR2

גרסת MVP: המתקשר אומר בעברית מה הוא מחפש; ימות המשיח מתמללת את הדיבור ושולחת את הטקסט ל-Cloudflare Worker. השרת מחפש בקטלוג של קבצים מורשים ומחזיר לימות הוראה להשמיע את הקובץ שכבר הועלה למערכת.

> המערכת אינה מורידה, מחלצת או משדרת אודיו מ-YouTube. יש להעלות למערכת ימות רק קובצי שמע שיש לכם הרשאה להשתמש בהם ולהשמיע אותם.

## מה כלול

- Cloudflare Worker ללא שרת קבוע.
- חיפוש עברי לפי כותרת ומילות מפתח.
- שימוש בזיהוי הדיבור המובנה של ימות המשיח (voice).
- השמעת הקובץ ישירות מתוך מערכת ימות.
- המשך חיפוש באותה שיחה, עד 25 חיפושים.
- בדיקות אוטומטיות.
- הגנה בסיסית באמצעות סוד משותף בנתיב API.

## 1. הכנת קובצי השמע בימות

העלה למערכת ימות קובצי WAV או OGG שיש לך הרשאה להשמיע. לדוגמה, אם הקובץ נמצא בשלוחה `/1` ושמו `001.wav`, הנתיב בקטלוג יהיה `/1/001` (בלי סיומת).

## 2. עריכת הקטלוג

ערוך את `catalog.js` והוסף שורה לכל קובץ:

```js
export const CATALOG = [
  {
    title: "שם ההקלטה",
    keywords: ["מילת חיפוש", "נושא נוסף"],
    file: "/1/001",
  },
  {
    title: "הקלטה נוספת",
    keywords: ["מילה נוספת", "נושא"],
    file: "/1/002",
  },
];
```

- `title`: הכותרת שתוקרא למתקשר.
- `keywords`: מילים נוספות שהמתקשר עשוי לומר.
- `file`: נתיב מלא בימות, ללא סיומת. הנתיב חייב להיות מספרי, למשל `/1/001` או `/1/2/003`.

## 3. פריסה ל-Cloudflare Workers

נדרש Node.js במחשב. שכפל את המאגר ופתח את תיקיית הפרויקט:

```bash
git clone https://github.com/7166810a-gif/youtube-offline-player-mvcrs21f.git
cd youtube-offline-player-mvcrs21f/yemot-ivr2
git checkout feature/yemot-ivr2-audio-search
```

התקן והתחבר:

```bash
npm install --save-dev wrangler
npx wrangler login
```

צור סוד ארוך (שמור אותו במקום בטוח):

```bash
openssl rand -hex 32
npx wrangler secret put YEMOT_SHARED_SECRET
```

כאשר Wrangler מבקש ערך, הדבק את אותו סוד. אל תכניס את הסוד לקוד או ל-GitHub.

בדיקות מקומיות:

```bash
npm test
```

פריסה:

```bash
npm run deploy
```

Cloudflare יציג כתובת בסגנון:

`https://yemot-ivr2-audio-search.<account>.workers.dev`

הכתובת שתוגדר בימות היא:

`https://yemot-ivr2-audio-search.<account>.workers.dev/yemot/<YOUR_SECRET>`

בדיקת תקינות:

`https://yemot-ivr2-audio-search.<account>.workers.dev/health`

## 4. הגדרות שלוחת API בימות המשיח

בשלוחה ייעודית, הגדר:

```ini
type=api
api_link=https://yemot-ivr2-audio-search.<account>.workers.dev/yemot/<YOUR_SECRET>
api_say_tts=no
api_wait_answer_music_on_hold=yes
```

החלף את `<account>` ואת `<YOUR_SECRET>` בפרטים שלך.

אין צורך להגדיר `api_000` או `api_001` בקובץ השלוחה: השרת מחזיר פעולת `read` ומבקש בכל פעם את הפרמטר הבא.

## 5. מהלך השיחה

1. בפנייה הראשונה השרת מחזיר `read=t-שלום, אמרו את שם קובץ השמע שאתם מחפשים=SEARCH1,,voice`.
2. ימות מזהה את הדיבור (עברית כברירת מחדל) ושולחת את התמלול לשרת בפרמטר `SEARCH1`.
3. השרת מוצא התאמה ומחזיר `id_list_message` שמשמיע את הכותרת ואז את הקובץ, ואחריו `read` לבקשת החיפוש הבא.
4. כל חיפוש נוסף מקבל שם פרמטר חדש (SEARCH2, SEARCH3 וכן הלאה).

## 6. עלויות ומגבלות

- Cloudflare Workers כולל מסלול חינמי עם מכסת בקשות יומית מוגבלת; בדוק את מכסת החשבון העדכנית לפני פריסה מסחרית.
- בשלב הזה אין צורך ב-R2: קובצי האודיו נשמרים בימות ו-Worker מחזיר רק פקודות טקסט קצרות.
- תיעוד ימות המשיח מציין שמודול API חינמי כרגע, אך הדבר עשוי להשתנות.
- שיחות, מספרי גישה ודקות תקשורת עשויים להיות בתשלום אצל ימות או ספק הטלפוניה. אי אפשר להבטיח אפס עלות כוללת.
- מנוע זיהוי הדיבור מובנה במודול API; הזמינות והמכסות כפופות למדיניות ימות.

## אבטחה ופרטיות

- אל תפרסם את כתובת ה-API המלאה עם הסוד.
- אל תשמור סודות בקבצי Git.
- ה-Worker אינו שומר תמלולים או הקלטות.
- מומלץ להשאיר את הקטלוג ואת המאגר פרטיים אם שמות הקבצים או המטא-נתונים אינם ציבוריים.

## תיעוד רשמי

- [מודול API של ימות המשיח](https://f2.freeivr.co.il/topic/56/מודול-api-תקשור-עם-מחשבים-וממשקי-נתונים-חיצוניים) — הגדרות API, voice, id_list_message ו-read.
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [Cloudflare Workers secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
