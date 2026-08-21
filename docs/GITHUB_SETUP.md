# Publishing Safe-Navi to GitHub

## Before pushing

```powershell
cd backend
.\venv\Scripts\python.exe -m pytest -q

cd ..
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
git status
```

Never commit `local.properties`, `google-services.json`, API keys, virtual environments or Android build folders.

## Push the existing repository

```powershell
git remote -v
git push origin main
git push origin --tags
```

## Connect a new GitHub repository

```powershell
git remote add origin https://github.com/YOUR-USERNAME/Safe-Navi.git
git branch -M main
git push -u origin main
git push origin --tags
```

## Dataset distribution

Do not add the 158 MB `road_segments.parquet` to ordinary Git history. GitHub rejects normal files larger than 100 MB. Use a GitHub Release asset, Git LFS, university storage or the included reproducible generator.

The repository already includes the 2.2 MB SQLite runtime and the compressed area-cell source required for the working 30% demonstration.
