# Safe Route Linear Risk Model

## Production selection

Safe-Navi uses the supplied linear regression artifact because it achieved the best measured performance in the teammate's completed comparison. Its held-out result was RMSE 1.4411, MAE 1.2478 and R2 0.9316. Five-fold street-grouped cross-validation produced RMSE 1.4434 +/- 0.0015 and R2 0.9325 +/- 0.0033. LightGBM and XGBoost remain valid comparison models, but both scored R2 0.927 on grouped cross-validation.

## Training scope

- 1,809,864 segment-time rows representing 452,466 physical OSM road segments
- Mumbai and Navi Mumbai
- Morning peak, midday, evening peak and night
- Ten synthetic risk factors plus road, location and time metadata
- Target: `synthetic_safety_risk_score`

The model is evidence that the application pipeline works; it is not evidence of real-world crime prediction accuracy. All factor values and the target are synthetic.

## Deployment format

The submitted scikit-learn pickle is converted by `backend/scripts/export_linear_model.py` into `safety_score_linear_v1.json`. Production inference reads only this non-executable JSON artifact and evaluates its 90 coefficients in pure Python. This avoids pickle code execution and removes scikit-learn, NumPy and joblib from the runtime dependency boundary.

Source model SHA-256: `FAB5CC4C3E48C0EE3059C82DC7ABFEAC8F5B7D6F242ED6586D0461FD67D2072E`

Source feature list SHA-256: `7758CCA7025B8675FBA5EE8762A8A3FC24E8E292FBBCB9D9BAF6DDFF866C237A`

## Runtime behavior

The API applies the trained model to point and route factor aggregates. Because this is a linear model, predicting the mean numeric factors is equivalent to averaging individual predictions. The compact runtime does not contain every original OSM categorical field, so road class, one-way, place type and related metadata use explicit unknown/default categories; the ten measured risk factors remain fully populated. Confidence combines the grouped-CV result with live dataset coverage and a conservative applicability discount.

Government-verified hazards are deliberately outside the learned score. They are combined after inference, and authoritative closures or critical no-go buffers exclude a route before ranking. ML cannot override an official closure.

## Known evaluation caveats

- The supplied single train/test split contains 336 street names on both sides, so grouped cross-validation is the preferred headline metric.
- The notebook's 0.009 bucketed classification result used three fixed bins against four quartile labels and is not a valid model metric. Safe-Navi does not expose or rely on it.
- The target is synthetic and near-linear, which explains why linear regression slightly outperformed the tree models.
- Real deployment requires retraining and validation on authorized, time-aligned, geographically representative real-world observations.
