# BeezList

אפליקציית Android TV לסטרימינג IPTV. טוענים פלייליסט M3U/M3U8 (מ-URL), והאפליקציה מציגה את הערוצים מקובצים לפי קטגוריה למעבר נוח עם שלט.

## Stack
- Kotlin + Jetpack Compose for TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`)
- Media3 ExoPlayer (HLS/IPTV playback)
- OkHttp לטעינת פלייליסטים, DataStore לשמירת כתובת הפלייליסט האחרונה

## הרצה
פתחו את הפרויקט ב-Android Studio (עם Android SDK מותקן), הריצו על מכשיר/אמולטור Android TV.

## מבנה
- `data/` — מודל ערוץ ופענוח M3U
- `ui/input` — מסך הזנת כתובת פלייליסט
- `ui/channels` — רשימת ערוצים מקובצת לפי קטגוריה
- `ui/player` — נגן מסך מלא מבוסס ExoPlayer
