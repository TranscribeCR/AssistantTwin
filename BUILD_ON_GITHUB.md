# AssistantTwin — GitHub APK build

1. Upload the contents of this project to a GitHub repository.
2. Make sure `app/`, `build.gradle`, `settings.gradle`, `gradle.properties`, and `.github/` are at the repository root.
3. Open **Actions** → **Build APK**.
4. Choose **Run workflow**.
5. When the build finishes, download the **AssistantTwin-debug-apk** artifact.

## Important
This project contains native C++/llama.cpp integration. The GitHub workflow initializes Git submodules, but the original ZIP may not contain the llama.cpp submodule contents. If the native build reports missing llama.cpp files, the repository must include the required submodule/source before the APK can compile.
