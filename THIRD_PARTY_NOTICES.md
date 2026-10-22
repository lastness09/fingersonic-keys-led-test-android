# Third-party notices

The original app and documentation are MIT licensed. The Gradle wrapper JAR and generated launcher scripts are provided by the Gradle project under Apache License 2.0. See `third_party/gradle-wrapper/LICENSE` and `third_party/gradle-wrapper/NOTICE`; the launcher scripts retain their original notices.

Build dependencies are downloaded from the SDK vendors and Maven repositories rather than bundled into this source archive:

- Android Gradle Plugin 8.7.3 and Android SDK tools: their respective Android/Google terms and licenses.
- Gradle 8.9: Apache License 2.0 and included notices.
- Microsoft OpenJDK 21: OpenJDK licensing and included notices.
- JUnit 4.13.2: Eclipse Public License 1.0, used only for host tests.
- Hamcrest Core 1.3: BSD license, JUnit's host test dependency.

There are no third-party runtime libraries in the app APK beyond the Android platform APIs. Proprietary Fingersonic device firmware is not included or licensed by this repository.
