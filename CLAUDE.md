# Проект

Форк `kittoku/Open-SSTP-Client` (MIT). Личный SSTP VPN-клиент под Android для подключения к домашнему роутеру Keenetic. Kotlin, без NDK.

План работ: `docs/fork-tasks.md`. Читать только текущую фазу.

# Не читать и не изменять

Протокольное ядро работает и оттестировано. Оно вне задач этого форка.

```
app/src/main/java/kittoku/osc/unit/
app/src/main/java/kittoku/osc/client/
app/src/main/java/kittoku/osc/cipher/
app/src/main/java/kittoku/osc/io/
app/src/main/java/kittoku/osc/SharedBridge.kt
```

~4400 строк. Ошибка здесь проявляется как «сервер молча закрыл соединение» — без стектрейса. Если кажется, что задача требует правки в этих файлах — остановись и спроси.

Изменения идут только в: `control/`, `service/`, `terminal/SSLTerminal.kt`, `preference/`, `activity/`, `ui/`, `res/`.

# Факты о кодовой базе

Не выяснять заново:

- `compileSdk` 37 (требование Compose BOM), `targetSdk` 36, AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7, 100% Kotlin
- UI: Jetpack Compose + Material 3 с dynamic color, всё в `ui/`. Навигация — состоянием в `ui/AppRoot.kt`, без navigation-compose. Старые `fragment/`, `preference/custom/`, `res/xml/*`, `res/layout/`, `res/menu/` удалены
- Строки: `res/values/` (английский) + `res/values-ru/`, `localeConfig` для системного выбора языка. Строки-значения (`LIST_TYPE_*`, `"DEFAULT"`, `AUTH_PROTOCOL_*`) переводить нельзя — они пишутся в prefs и в JSON-профили
- Настройки: `SharedPreferences` через `preference/accessor/*.kt`, ключи в `preference/constant.kt` (enum `OscPrefKey`). Для Compose — `ui/PrefsRepository.kt` со `StateFlow` поверх тех же ключей
- `SharedPreferences` читается синхронно в конструкторах протокольного слоя. **Не мигрировать на DataStore** — это потянет правки в запретных файлах
- `OscPrefKey.ROOT_STATE` — канал «сервис ↔ плитка в шторке», отвечает только на вопрос «сервис жив». Экран берёт градацию из `HOME_STATE` (`STATE_*`) и `HOME_CONNECTED_AT`
- `OscPrefKey.HOME_STATUS` — статус соединения парами `KEY=value`, пишет `control/NetworkObserver.kt`, разбирает `ui/Status.kt`. Подписи рисует UI, поэтому статус переводится
- `control/NetworkObserver.kt` вопреки названию слушает собственный туннель и только собирает текст статуса. Реконнекта в нём нет
- `Controller.kill()` намеренно отрабатывает один раз на экземпляр (`mutex.tryLock()` без разблокировки). Это не баг
- `terminal/SSLTerminal.kt`: `protect()` вызывается после HTTP-негоциации. Безопасно только пока tun поднимается позже

# Как работать

- Одна фаза за сессию. После фазы — коммит и `/clear`.
- Проверка — сборкой и запуском, не перечитыванием кода.
- Не пересказывать план перед началом и не писать отчёт после. Делать и говорить, что сделано, одним абзацем.
- Не запускать поиск по всему репозиторию: структура описана выше и в плане.
- Отклонение от плана — только после вопроса.
