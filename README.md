# Lords of the poker room — native Android

Полностью нативная Android-версия игры. Внутри **нет WebView и HTML**: стол, карты, игроки, кнопки, фишки, конфетти и фейерверки рисуются Android `Canvas`, поэтому интерфейс рассчитывается по реальному размеру экрана и не требует прокрутки браузера.

## Режимы
- **Badass Holdem** — No-Limit Texas Hold'em, случайный состав 2–5 соперников, блайнды 10/20, fold/check/call/raise/all-in, flop/turn/river, определение комбинаций, вскрытие и side pots.
- **BlackJackPorter** — 6 колод, hit/stand/double/split, blackjack 3:2, insurance; пользовательское правило проекта: **дилер добирает только до 15 и останавливается на 16**.

## Визуал и эффекты
- адаптивный нативный стол для портретной и горизонтальной ориентации;
- квадратные аватары и случайный состав персонажей;
- текущая комбинация игрока и комбинации на вскрытии;
- анимированные фишки;
- конфетти, фейерверки и победная мелодия;
- сохранение общего количества фишек и настройки звука через SharedPreferences.

## Сборка APK в Android Studio
1. Открыть эту папку как проект.
2. Дождаться синхронизации Gradle.
3. `Build` → `Build App Bundle(s) / APK(s)` → `Build APK(s)`.
4. Debug APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Автоматическая сборка GitHub Actions
В проекте есть `.github/workflows/build-apk.yml`. После загрузки проекта в GitHub workflow соберёт APK и положит его в Artifacts под именем **Lords-of-the-poker-room-APK**.


## Native Patch 1

See `PATCH_1_NOTES.md` for the first large native update.
