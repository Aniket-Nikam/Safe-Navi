# Safe-Navi dataset API

This FastAPI service makes the synthetic Mumbai–Navi Mumbai dataset usable by the Android application without embedding the 158 MB Parquet analytics table inside the APK.

The included SQLite database contains the dataset's complete 1 km area-cell aggregation: 6,508 rows across four time periods and all ten factors. It is generated reproducibly from `area_cells.csv.gz`. The full `road_segments.parquet` remains the street-level training and future PostGIS import source.

## Run

```powershell
cd backend
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Open `http://127.0.0.1:8000/docs`. Android emulators reach the host as `http://10.0.2.2:8000`.

## Rebuild SQLite

```powershell
.\.venv\Scripts\python.exe scripts\build_runtime_db.py `
  --source data\source\area_cells.csv.gz `
  --manifest data\source\manifest.json `
  --output data\safe_navi_runtime.sqlite
```

All returned safety values are synthetic classroom data, not observed safety measurements.
