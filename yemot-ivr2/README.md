# חיפוש YouTube והשמעת אודיו מורשה דרך ימות המשיח IVR2

## חשוב לפני שמתחילים

המערכת יכולה לחפש סרטונים ב-YouTube, אבל YouTube Data API אינו מספק כתובת MP3. מדיניות YouTube אוסרת להפריד/לחלץ את פס הקול מתוך תוכן YouTube או לנגן את האודיו שלו ברקע. לכן המערכת **אינה מורידה אודיו מיוטיוב**.

כדי להשמיע אודיו בשיחת טלפון, צריך קובץ אודיו נפרד שסופק על ידי בעל הזכויות, עם הרשאה מפורשת להעתקה/המרה ולהשמעה טלפונית. המערכת תחפש את הסרטון ב-YouTube, תקריא את התוצאות, תבקש לבחור מספר, ואז תעלה לימות את קובץ המקור המורשה ותשמיע אותו.

אם אין מקור אודיו עצמאי ומאושר עבור הווידאו שנבחר, המערכת לא תשמיע אותו.

## זרימת השיחה

1. המתקשר מסכים לתנאי השירות ולמדיניות הפרטיות.
2. הוא אומר בעברית מה לחפש.
3. השרת מחפש עד חמש תוצאות ב-YouTube Data API.
4. ימות מקריאה את שמות התוצאות ומבקשת להקיש 1–5.
5. השרת בודק אם הוגדר מקור אודיו עצמאי ומורשה עבור מזהה הסרטון שנבחר.
6. אם קיים מקור מורשה, השרת מוריד את קובץ המקור העצמאי, מעלה אותו לימות דרך UploadFile (עם המרה ל-WAV), ואז מחזיר ל-IVR הוראת השמעה של הקובץ המקומי.
7. אם אין מקור כזה, השרת אומר שאין הרשאה/מקור נפרד ומאפשר חיפוש חדש.

## 1. יצירת מפתח YouTube API

1. פתח פרויקט ב-Google Cloud Console.
2. הפעל YouTube Data API v3.
3. צור API key והגבל אותו ל-YouTube Data API v3.
4. אל תעלה את המפתח ל-GitHub ואל תשלח אותו בצ'אט.

מכסת ברירת המחדל של `search.list` היא 100 קריאות ביום לפרויקט. Cloudflare Cache שומר תוצאות למשך 5 דקות כדי שבחירת התוצאה לא תבצע חיפוש נוסף כל עוד התוצאות עדיין במטמון.

## 2. הנפקת API Key לימות המשיח

במערכת ימות המשיח, פתח את ממשק חומת האש / API Key והפק מפתח קבוע שמוגבל להרשאות הנדרשות לניהול קבצי IVR2 ולהעלאת קבצים בלבד, ככל שניתן להגדיר הרשאות כאלה בחשבונך.

אל תשתף את המפתח בצ'אט או ב-GitHub. נשתמש בו רק כסוד Cloudflare בשם `YEMOT_API_TOKEN`.

ה-API הרשמי של ימות מתעד את פעולת `UploadFile`, קבלת token בכותרת `authorization`, העלאה באמצעות multipart/form-data, והמרת MP3/OGG/WMA ל-WAV לטלפוניה כאשר `convertAudio=1`.

## 3. מקור אודיו מורשה

הוסף ב-`licensed-audio.js` מיפוי בין מזהה סרטון YouTube לבין קובץ אודיו נפרד שסיפק בעל הזכויות. אין להכניס לשם קישור watch של YouTube, youtu.be או googlevideo.

דוגמה בלבד:

```js
export const LICENSED_AUDIO = {
  "VIDEO_ID": {
    sourceUrl: "https://media.example.com/audio/original-master.mp3",
    rightsConfirmed: true,
    rightsNote: "אישור כתוב מבעל הזכויות מתאריך YYYY-MM-DD"
  }
};
```

חשוב:
- `VIDEO_ID` הוא המזהה המדויק של הסרטון בתוצאות YouTube.
- `sourceUrl` הוא קישור HTTPS ישיר לקובץ MP3/OGG/WAV נפרד, שאותו קיבלת מבעל הזכויות.
- `rightsConfirmed` יהיה true רק לאחר שבדקת שההרשאה מכסה העתקה, המרה והשמעה בטלפון.
- `rightsNote` הוא תיעוד פנימי שלך.
- כתובת השרת שמארח את קובץ האודיו חייבת להיכלל בסוד `AUTHORIZED_AUDIO_HOSTS`.
- הקוד חוסם במפורש דומיינים של YouTube ושל Googlevideo כמקורות אודיו.

אין להכניס ל-GitHub קישור פרטי או סוד גישה של ספק המדיה. השתמש בקישור HTTPS ציבורי מאובטח או במנגנון חתימה זמני שיתוכנן בנפרד. אם הקובץ מוגן, נצטרך להתאים את האימות שלו ולא לפרסם את פרטי הגישה.

## 4. פריסת Cloudflare Workers

נדרש Node.js:

```bash
git clone https://github.com/7166810a-gif/youtube-offline-player-mvcrs21f.git
cd youtube-offline-player-mvcrs21f/yemot-ivr2
git checkout feature/yemot-ivr2-audio-search
npm install
npm install --save-dev wrangler
npx wrangler login
```

הגדר סודות (כל פקודה תבקש ממך להדביק את הערך):

```bash
npx wrangler secret put YEMOT_SHARED_SECRET
npx wrangler secret put YOUTUBE_API_KEY
npx wrangler secret put YEMOT_API_TOKEN
npx wrangler secret put AUTHORIZED_AUDIO_HOSTS
```

- `YEMOT_SHARED_SECRET`: סיסמה אקראית לנתיב ה-API של ה-IVR.
- `YOUTUBE_API_KEY`: המפתח שיצרת ב-Google Cloud.
- `YEMOT_API_TOKEN`: מפתח API קבוע של ימות עם הרשאת העלאת קבצים ל-IVR2.
- `AUTHORIZED_AUDIO_HOSTS`: שם מארח מדויק, למשל `media.example.com`. ניתן להכניס מספר מארחים מופרדים בפסיקים.

אפשר לשנות את תיקיית היעד בימות באמצעות משתנה `YEMOT_AUDIO_DIR` (ברירת מחדל `YT_AUDIO`).

בדיקות ופריסה:

```bash
npm test
npm run deploy
```

## 5. הגדרת שלוחה בימות

צור שלוחה מסוג API, והגדר:

```ini
type=api
api_link=https://yemot-youtube-search.<account>.workers.dev/yemot/<YEMOT_SHARED_SECRET>
api_say_tts=no
api_wait_answer_music_on_hold=yes
api_phone_send=no
api_did_send=no
api_call_id_send=no
api_extension_send=no
api_enter_id_send=no
api_enter_id_name_send=no
api_time_send=no
api_hangup_send=no
```

החלף את `<account>` ואת `<YEMOT_SHARED_SECRET>` בפרטים ש-Cloudflare נתן לך. השם המדויק של השלוחה והנתיב הם לפי מבנה המערכת שלך.

## 6. בדיקה מקצה לקצה

1. ודא שיש לפחות סרטון אחד ב-YouTube שתוצאת החיפוש שלו צפויה.
2. ודא שקובץ האודיו העצמאי שלו קיים בכתובת שסיפק בעל הזכויות ושהדומיין מוגדר ב-`AUTHORIZED_AUDIO_HOSTS`.
3. ודא שהמיפוי ב-`licensed-audio.js` תואם למזהה הסרטון.
4. ודא שמפתח ימות מסוג API Key תקף ומוגבל ל-UploadFile ולתיקיית ה-IVR2 הדרושה.
5. התקשר למספר ימות, הקש 1 להסכמה, אמור את נושא החיפוש, הקש את מספר התוצאה ובדוק שהקובץ שהועלה מושמע.
6. בדוק ביומן הבקשות של Cloudflare ובתיקיית `YT_AUDIO` בימות שהעלאה והמרה הצליחו.

## מגבלות ועלויות

- YouTube Data API: מכסת ברירת מחדל של 100 קריאות חיפוש ביום; ניתן לבקש הרחבה לאחר ביקורת תאימות.
- Cloudflare Workers: יש מסלול חינמי, אבל יש מגבלות בקשות, CPU וזיכרון. קבצים מוגבלים כאן ל-45 MiB כדי להשאיר מרווח מתחת למגבלת ההעלאה המתועדת בימות (50 MB, לפי התיעוד מ-28.09.2022).
- ימות המשיח: מודול IVR API מתועד כחינמי כרגע, אך ייתכן שישתנה. API ניהול הקבצים והעלאה דורש מפתח API; עלויות/יחידות אפשריות יש לבדוק בחשבון.
- שיחות טלפון ודקות תקשורת אינן בהכרח בחינם.
- קבצים שנבחרים יועלו לימות וישמרו בתיקייה `YT_AUDIO`; נדרש טיפול במחיקה תקופתית/מכסת אחסון לפני הפעלה בקנה מידה גדול. הקוד הנוכחי משתמש באותו שם קובץ לפי Video ID ולכן מעלה מחדש בעת כל השמעה.

## פרטיות ותנאי שימוש

השרת אינו שומר שאילתות או הקלטות במסד נתונים, אך מעביר את מילות החיפוש ל-YouTube ולבעל שירות האודיו. קובץ האודיו שסופק עצמאית מועבר לימות לצורך ההשמעה. יש לקבל הסכמה מהמשתמשים ולהציג את דפי `/privacy` ו-`/terms` לפני השימוש הציבורי.

## מקורות רשמיים

- [YouTube API Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [YouTube Search API](https://developers.google.com/youtube/data/reference/rest/v3/search/list)
- [ימות המשיח — מודול API](https://f2.freeivr.co.il/topic/56/מודול-api-תקשור-עם-מחשבים-וממשקי-נתונים-חיצוניים)
- [ימות המשיח — API UploadFile](https://f2.freeivr.co.il/topic/55/api-גישת-מפתחים-למערכות)
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
