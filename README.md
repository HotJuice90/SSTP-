# SSTP+

Личный SSTP VPN-клиент под Android для подключения к домашнему роутеру Keenetic.

Форк [kittoku/Open-SSTP-Client](https://github.com/kittoku/Open-SSTP-Client) (MIT).
Протокольное ядро (SSTP/PPP, MS-CHAPv2, crypto binding) взято из апстрима без изменений;
доработки касаются сетевого lifecycle, дефолтов и UI.

- `applicationId`: `home.keenetic.sstp` (ставится рядом со стоковым Open SSTP Client)
- `minSdk` 26, `targetSdk` 36

## Скачать

Готовые сборки — на странице [релизов](https://github.com/HotJuice90/SSTP-/releases). Скачай APK, открой на телефоне, разреши установку из этого источника.

Требуется Android 8.0 или новее. Сборки подписаны отладочным ключом: обновляться поверх ими можно, а переход на релизную подпись потребует удалить приложение, поэтому настройки перед этим лучше выгрузить в файл.

## Атрибуция

Copyright (c) 2020 kittoku — исходный код распространяется по лицензии MIT, см. [LICENSE](LICENSE).
Оригинальный README апстрима сохранён как [README-upstream.md](README-upstream.md).

## Разработка

Справочник по настройкам: [docs/settings.md](docs/settings.md).
План работ по форку: [docs/fork-tasks.md](docs/fork-tasks.md).
Правила для агентов и границы «не трогать»: [CLAUDE.md](CLAUDE.md).

Апстрим подключён как remote `upstream`, фиксы протокола тянутся оттуда:

```
git fetch upstream
```
