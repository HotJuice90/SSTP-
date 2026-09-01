# Форк Open-SSTP-Client: задание на доработку

Репозиторий-основа: `https://github.com/kittoku/Open-SSTP-Client`, лицензия MIT, ветка `main` (versionName 1.10.3, 205 коммитов).

**Цель форка:** личный VPN-клиент для подключения Android-телефона к домашнему роутеру Keenetic по SSTP, с современным UI и надёжным переподключением при смене мобильной сети.

Состояние базы на момент составления задания: `compileSdk`/`targetSdk` 36, AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, 100% Kotlin, ~7100 строк.

---

## ГРАНИЦЫ: что нельзя трогать

Протокольное ядро работает и оттестировано. Ошибка в нём проявляется как «сервер молча рвёт соединение», без стектрейса и без полезного лога. Отладка стоит дни.

**Не изменять, не рефакторить, не «улучшать» эти каталоги:**

```
app/src/main/java/kittoku/osc/unit/          # парсинг SSTP и PPP пакетов
app/src/main/java/kittoku/osc/client/        # SSTP + PPP state machines
app/src/main/java/kittoku/osc/cipher/        # crypto binding, MS-CHAPv2
app/src/main/java/kittoku/osc/io/            # насосы incoming/outgoing
app/src/main/java/kittoku/osc/SharedBridge.kt
```

Особенно `cipher/hash.kt` и `client/ppp/auth/` — там вывод HLAK из MPPE-ключей MS-CHAPv2 для Compound MAC. Порядок байт там неочевиден и не документирован в коде.

Изменения затрагивают ровно три вещи: **сетевой lifecycle** (`control/`, `service/`, `terminal/SSLTerminal.kt`), **дефолты** (`preference/constant.kt`, `configuration.kt`) и **UI** (`activity/`, `fragment/`, `preference/custom/`, `res/xml/`, `res/layout/`).

Положи в корень форка `CLAUDE.md` со списком выше, чтобы правило пережило контекст.

---

## Порядок работ

Пять фаз, каждая — отдельная ветка и коммит. После каждой фазы приложение должно собираться и подключаться к серверу. Не начинать следующую, не проверив предыдущую на живом Keenetic.

---

## Фаза 0. Ребрендинг и базовая настройка

**Файлы:** `app/build.gradle`, `AndroidManifest.xml`, `res/values/strings.xml`, `res/mipmap-*`

1. `namespace` и `applicationId`: `kittoku.osc` → своё (например `home.keenetic.sstp`). Пакеты исходников можно оставить `kittoku.osc` — переименование 80 файлов ради косметики не окупается, и оно ломает git-историю апстрима, из которой ты потом будешь тянуть фиксы.
2. `versionCode` 1, `versionName` "1.0.0", своё имя приложения и иконка.
3. `minSdk` 23 → **26**. Обоснование: `registerDefaultNetworkCallback` требует API 24, нужен в фазе 2; на 26 также исчезают ветки `if (SDK_INT >= O)` вокруг notification channels. Android 8 и ниже поддерживать смысла нет.
4. Сохранить `LICENSE` и добавить в `README` атрибуцию апстрима — это требование MIT, не формальность.
5. Настроить remote `upstream` на исходный репозиторий, чтобы тянуть фиксы протокола.

**Критерий:** собирается, ставится рядом со стоковым OSC, подключается.

---

## Фаза 1. Дефолты

**Файлы:** `configuration.kt`, `preference/constant.kt`

```kotlin
// configuration.kt
internal const val DEFAULT_MTU = 1350   // было 1500
internal const val DEFAULT_MRU = 1500   // НЕ трогать
```

MTU — это то, что мы отдаём в `VpnService.Builder.setMtu()`, размер исходящих кадров. MRU — сколько мы готовы принять от пира. Они не обязаны совпадать, и уменьшать MRU не надо.

Почему 1350: снаружи 20 (IPv4) + 20 (TCP) + 22 (TLS 1.3 record + AEAD tag) + 4 (SSTP) + 4 (PPP) = 70 байт оверхеда, то есть верхняя граница 1430. Запас взят на операторов с path MTU меньше 1500; ICMP «Fragmentation Needed» в мобильных сетях фильтруется, так что PMTUD не сработает и симптом будет «половина сайтов не открывается».

```kotlin
// preference/constant.kt, DEFAULT_BOOLEAN_MAP
OscPrefKey.RECONNECTION_ENABLED to true,   // было false
```

`RECONNECTION_INTERVAL` (сейчас 10) в фазе 2 станет базой экспоненциального backoff. `RECONNECTION_COUNT` (сейчас 3) перестанет использоваться — см. ниже.

**Критерий:** в статусе подключения виден MTU 1350, тяжёлые сайты открываются.

---

## Фаза 2. Реконнект — главная задача

### Что сейчас не так

Три отдельные проблемы:

1. **`control/NetworkObserver.kt` не наблюдает за сетью.** Вопреки названию, он регистрирует колбэк на `TRANSPORT_VPN` с `removeCapability(NET_CAPABILITY_NOT_VPN)` — то есть слушает собственный туннель — и единственное, что делает, это собирает текстовую сводку в `OscPrefKey.HOME_STATUS` для отображения в UI. Смену Wi-Fi↔LTE он не видит по построению.

2. **Разрыв обнаруживается только по таймауту эха.** `io/incoming/IncomingManager.kt`: `SSTP_ECHO_INTERVAL = 20_000L`, `PPP_ECHO_INTERVAL = 20_000L`. `EchoTimer` шлёт эхо после 20 с тишины и объявляет пира мёртвым ещё через 20 с. При переходе в LTE туннель висит мёртвым до 40 секунд, хотя система знала о смене сети мгновенно.

3. **Попытки конечны и с фиксированной паузой.** `RECONNECTION_COUNT = 3`, `RECONNECTION_INTERVAL = 10`. `Controller.isReconnectionAvailable` проверяет `RECONNECTION_LIFE > 0`, счётчик декрементируется в `SstpVpnService.launchJobReconnect()`. В метро три жизни сгорают за полминуты, дальше туннель мёртв до ручного перезапуска.

### Что сделать

#### 2.1 Новый наблюдатель за нижележащей сетью

Создать `control/UnderlyingNetworkObserver.kt`. Ключевое отличие от существующего `NetworkObserver`: он живёт на уровне **сервиса**, а не `Controller`, потому что должен пережить смерть соединения и рестарт клиента.

```kotlin
package kittoku.osc.control

import android.net.ConnectivityManager
import android.net.Network
import kittoku.osc.service.SstpVpnService

internal class UnderlyingNetworkObserver(
    private val service: SstpVpnService,
    private val onChanged: (Network?) -> Unit,
) {
    private val manager = service.getSystemService(ConnectivityManager::class.java)
    private var current: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Дефолтная сеть системы. Собственный туннель сюда не попадает:
            // VPN становится дефолтом только для приложений, а сервис
            // исключён из него через protect()/addDisallowedApplication.
            if (network != current) {
                current = network
                onChanged(network)
            }
        }

        override fun onLost(network: Network) {
            if (network == current) {
                current = null
                onChanged(null)
            }
        }
    }

    internal val currentNetwork: Network?
        get() = current

    internal fun start() = manager.registerDefaultNetworkCallback(callback)

    internal fun close() {
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {} // уже снят
    }
}
```

Существующий `control/NetworkObserver.kt` **оставить как есть** — он делает свою работу (сводка для UI) и к реконнекту отношения не имеет. Не объединять их: у них разное время жизни.

#### 2.2 Экспоненциальный backoff вместо конечных жизней

В `service/SstpVpnService.kt` заменить `launchJobReconnect()`:

```kotlin
private val backoffSeconds = intArrayOf(1, 2, 4, 8, 15, 30, 60)
private var reconnectAttempt = 0

internal fun launchJobReconnect() {
    jobReconnect = scope.launch {
        try {
            val delaySec = backoffSeconds[
                minOf(reconnectAttempt, backoffSeconds.lastIndex)
            ]
            reconnectAttempt++

            val message = "Reconnecting in ${delaySec}s (attempt $reconnectAttempt)"
            notifyMessage(message, NOTIFICATION_RECONNECT_ID, NOTIFICATION_RECONNECT_CHANNEL)
            logWriter?.report(message)

            delay(delaySec * 1000L)
            initializeClient()
        } catch (_: CancellationException) {
        } finally {
            cancelNotification(NOTIFICATION_RECONNECT_ID)
        }
    }
}
```

`reconnectAttempt = 0` сбрасывается в двух местах: при `ACTION_VPN_CONNECT` и при успешном подключении. Второе — в `Controller.launchJobMain()`, там где сейчас стоит `resetReconnectionLife(bridge.prefs)` после создания `NetworkObserver`; заменить на вызов сервиса.

В `Controller` убрать ограничение по жизням:

```kotlin
private val isReconnectionAvailable: Boolean
    get() = true   // было: getIntPrefValue(RECONNECTION_LIFE, prefs) > 0
```

Ключи `RECONNECTION_COUNT` и `RECONNECTION_LIFE` и функцию `resetReconnectionLife()` в `preference/accessor/int.kt` после этого можно удалить вместе с их пунктами в UI — они больше ни на что не влияют.

#### 2.3 Мгновенный реконнект по смене сети

В `SstpVpnService`:

```kotlin
private var networkObserver: UnderlyingNetworkObserver? = null

// в onStartCommand при ACTION_VPN_CONNECT, после initializeClient():
networkObserver = UnderlyingNetworkObserver(this) { network ->
    onUnderlyingNetworkChanged(network)
}.also { it.start() }

private fun onUnderlyingNetworkChanged(network: Network?) {
    if (network == null) return   // сети нет — ждём появления, дёргаться незачем

    setUnderlyingNetworks(arrayOf(network))

    scope.launch {
        jobReconnect?.cancelAndJoin()
        reconnectAttempt = 0       // новая сеть — backoff с нуля
        val c = controller
        if (c != null) {
            c.kill(true, null)     // сам вызовет launchJobReconnect()
        } else {
            initializeClient()
        }
    }
}
```

Снимать наблюдатель в `onDestroy()` и в ветке дисконнекта `onStartCommand`.

**Тонкость про `Controller.kill()`.** В нём стоит `if (!mutex.tryLock()) return`, и мьютекс никогда не отпускается — это намеренно, kill отрабатывает ровно один раз на экземпляр `Controller`. Если смена сети придёт, когда kill уже выполняется, второй вызов молча выйдет, но первый всё равно дойдёт до `launchJobReconnect()`. Поведение корректное, ломать эту логику не надо.

#### 2.4 Порядок protect() — обязательно в этой же фазе

**`terminal/SSLTerminal.kt`.** Сейчас `bridge.service.protect(socket)` вызывается **строкой 318**, то есть после TLS-хендшейка и HTTP-негоциации. Это работает только потому, что tun-интерфейс на тот момент ещё не поднят — `IPTerminal.initialize()` вызывается позже, после IPCP.

Как только реконнект станет быстрым и агрессивным, гонка становится реальной: сокет успевает создаться раньше, чем закрылся старый интерфейс. Результат — маршрут `0.0.0.0/0` заворачивает сам TLS-сокет в туннель, петля, соединение не встаёт никогда и без внятной ошибки.

Правка в `establishSSL()`:

```kotlin
// БЫЛО:
socket = Socket(socketHostname, socketPort).also {
    socketInputStream = it.getInputStream()
    socketOutputStream = it.getOutputStream()
}

// СТАЛО:
socket = Socket().also {
    // protect() до connect(), на ещё не подключённом сокете
    bridge.service.protect(it)
    it.connect(InetSocketAddress(socketHostname, socketPort), 10_000)
    socketInputStream = it.getInputStream()
    socketOutputStream = it.getOutputStream()
}
```

И удалить `bridge.service.protect(socket)` из `establishHttp()` (строка 318), оставив там только `socket!!.soTimeout = 1_000`.

#### 2.5 Кэш резолва хоста

`Socket.connect(InetSocketAddress(hostname, port))` резолвит имя внутри. Пока tun пересоздаётся при каждом реконнекте, системный DNS в этот момент доступен и всё работает. Но это тоже гонка: если интерфейс ещё жив, DNS указывает на роутер, который недостижим, и реконнект падает на резолве.

Резолвить один раз при первом успешном подключении и хранить `InetAddress` в сервисе (переживает пересоздание `Controller`), передавая в `connect()` уже адрес. Если коннект по кэшу упал — сбросить кэш и следующую попытку делать по имени.

**Критерии фазы 2:**
- Переключение Wi-Fi → мобильные данные: туннель восстанавливается за 1–3 секунды, не за 40.
- Режим полёта на 2 минуты и обратно: восстанавливается сам, без ручного тапа.
- Туннель, оставленный на ночь при выключенном экране, утром жив.
- В логе видно, что backoff растёт при недоступном сервере и сбрасывается при появлении сети.

---

## Фаза 3. Мелочи манифеста

**`AndroidManifest.xml`:**

1. `android:foregroundServiceType="specialUse"` — оставить можно, работает. Но `specialUse` требует объявления `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, которого в манифесте нет; для сайдлоада это не проверяется, для Google Play — блокер. Документированная категория для VPN — `systemExempted`, и она, в отличие от прочих типов, не требует парного разрешения `FOREGROUND_SERVICE_*`. Если планируется только сайдлоад — не трогай, работает и так.
2. `QUERY_ALL_PACKAGES` нужен для выбора приложений в split tunneling. Для сайдлоада нормально; в Play требует обоснования.
3. Добавить, чтобы получить системный killswitch бесплатно:
   ```xml
   <meta-data
       android:name="android.net.VpnService.SUPPORTS_ALWAYS_ON"
       android:value="true" />
   ```
   внутри `<service android:name=".service.SstpVpnService">`. После этого в системных настройках VPN появится «Always-on» и «Block connections without VPN» — своя реализация killswitch не нужна.

---

## Фаза 4. UI на Compose

Самый объёмный кусок, но и самый безопасный: протокола не касается.

### Что есть сейчас

`activity/MainActivity.kt` (249 строк) — `AppCompatActivity` с `ViewPager2` и двумя вкладками, обе `PreferenceFragmentCompat`. Экраны описаны в `res/xml/home.xml`, `settings.xml`, `apps.xml`. Кастомные preference-виджеты в `preference/custom/` (12 файлов, ~650 строк). Плюс `fragment/` (4 файла), `activity/BlankActivity.kt`.

### Критическое архитектурное требование

**Оставить `SharedPreferences` как единственный источник истины. Не мигрировать на DataStore.**

Причина: весь протокольный слой читает настройки напрямую через `getStringPrefValue(OscPrefKey.X, prefs)` — в `SharedBridge`, `SSLTerminal`, `IPTerminal`, `Controller`, `IncomingManager`. Миграция хранилища потянет за собой правки в файлах из списка «не трогать», а асинхронный `DataStore` ещё и не даст читать значения синхронно в конструкторах, как это делается сейчас.

Правильный подход: обернуть prefs в репозиторий со `StateFlow` для Compose, оставив запись в те же `SharedPreferences` через существующие акцессоры `preference/accessor/*.kt`. Compose читает через flow, протокол читает напрямую — оба видят одно и то же.

```kotlin
class PrefsRepository(private val prefs: SharedPreferences) {
    fun <T> flowOf(key: OscPrefKey, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key.name) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
```

`OscPrefKey.ROOT_STATE` уже используется как канал «сервис ↔ UI» через listener — на нём же строится состояние кнопки подключения и обновление плитки.

### Сборка

`app/build.gradle`:
- `jvmTarget` и `compileOptions` с 1.8 на **11** — минимум для Compose
- плагин `org.jetbrains.kotlin.plugin.compose` (на Kotlin 2.x это отдельный плагин, а не `composeOptions`)
- `buildFeatures { compose true }`, `viewBinding` можно выключить после удаления фрагментов
- зависимости: `androidx.compose:compose-bom`, `material3`, `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`

### Экраны

1. **Главный.** Крупная карточка состояния (Disconnected / Connecting / Connected / Reconnecting), кнопка-переключатель, под ней хост и время в соединении. При подключении — данные из `HOME_STATUS`: TLS-версия и cipher suite, выданный IP, DNS, таблица маршрутов.
2. **Профиль подключения.** Хост, порт, логин, пароль. Пароль — `PasswordVisualTransformation`, `KeyboardType.Password`.
3. **Настройки.** Сгруппировать по префиксам `OscPrefKey`: SSL, PPP, DNS, ROUTE, RECONNECTION, LOG. Редко используемое (PROXY, выбор cipher suites, кастомный trust store, кастомный SNI) убрать под «Дополнительно» — это отладочные опции автора, в повседневном сценарии они не нужны.
4. **Выбор приложений** для split tunneling — вместо `fragment/AppsFragment.kt`. Список с иконками, поиск, переключатель allowed/disallowed. Не забыть: у OSC в описании отмечено, что при app-based rules нужно включать `ROUTE_DO_ADD_DEFAULT_ROUTE`, иначе выбранные приложения всё равно не пойдут в туннель.
5. **Профили** — импорт/экспорт JSON уже реализован в `preference/profile.kt` на kotlinx-serialization, переиспользовать логику, заменить только вызывающий UI.

### Удалить после переноса

```
activity/MainActivity.kt        → заменить на ComponentActivity + setContent
activity/BlankActivity.kt       → проверить: используется для сохранения сертификата из уведомления
fragment/                       → всё
preference/custom/              → всё
res/xml/home.xml, settings.xml, apps.xml, blank_preference.xml
res/layout/                     → всё, кроме того что нужно BlankActivity
```

Оставить: `preference/accessor/`, `preference/constant.kt`, `preference/profile.kt`, `preference/app.kt`, `preference/check.kt`.

### Плитка

`service/SstpTileService.kt` (104 строки) уже работает через `ACTIVE_TILE` и `ROOT_STATE`, от UI не зависит. Не трогать, только проверить после замены активити, что `QS_TILE_PREFERENCES` intent-filter переехал на новую активити.

**Критерий:** тёмная тема, Material 3, подключение в один тап с главного экрана и из шторки, все настройки из старого UI доступны.

---

## Итоговая проверка

На живом Keenetic, с телефона в мобильной сети:

1. Туннель поднимается, `Connected` в UI, ключ в статус-баре.
2. Внешний IP — тот, который отдаёт маршрутизация роутера. Если это IP домашнего провайдера, а не ожидаемый выходной узел — проблема не в приложении, а в правилах на роутере: SSTP-клиенты приземляются на отдельном интерфейсе и могут не попадать в цепочку перехвата.
3. Домашние имена резолвятся (DNS роутера применяется).
4. Wi-Fi ↔ LTE ↔ режим полёта — восстановление автоматическое, за секунды.
5. Ночь при выключенном экране — соединение живо.
6. Тяжёлые сайты и большие загрузки без зависаний (проверка MTU).
