# OmniJaws for exTHmUI 12

OmniJaws is a small weather service, signed system content provider, and optional
home-screen widget for exTHmUI 12.

## Defaults and privacy

- The service and lock-screen weather are disabled by default.
- The default provider is MET Norway, using metric units and a two-hour interval.
- Automatic location accepts Android 12 approximate or precise foreground location.
  Periodic automatic-location updates are scheduled only after the user separately
  grants background location in Android settings.
- Manual-city mode does not require location permission.
- Network requests use HTTPS only. Cleartext traffic is disabled.

No shared provider credential is included in the ROM. OpenWeatherMap is optional and
does not send a request until the user enters their own key in OmniJaws settings. Keys
are stored in credential-protected app preferences and are not shown in summaries or
written to logcat. Users can obtain a key from:

https://openweathermap.org/appid

## Provider access

Weather and settings data are exposed at:

```text
content://org.omnirom.omnijaws.provider/weather
content://org.omnirom.omnijaws.provider/settings
```

The provider requires the signature permission
`org.omnirom.omnijaws.READ_WEATHER`. System clients should observe these URIs for
changes. Update/error broadcasts are restricted to the OmniJaws package and are not an
external client API.

## Condition icon packs

An icon-pack activity can advertise the action `org.omnirom.WeatherIconPack`. The
activity name supplies the drawable prefix, for example `outline_32`. Chronus-compatible
packs using the category `com.dvtonder.chronus.ICON_PACK` are also discoverable.

The bundled outline set was imported by the upstream OmniJaws project with attribution
to Emske Ltd. Redistribution status for other historical icon sets should be verified
before publishing a new source repository.

## Upstream

This Android 12 port is based on Corvus-AOSP OmniJaws commit
`902a9682aa56b10ca9758cb26cb6e55a8a0fde64`. Individual source files retain their
upstream GPL-2.0 or Apache-2.0 headers.
