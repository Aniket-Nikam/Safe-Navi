from __future__ import annotations

import os
import html
from datetime import datetime, timezone
from typing import Annotated, Literal
from urllib.parse import quote

import httpx
from fastapi import APIRouter, Depends, File, Header, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect, status
from fastapi.responses import FileResponse, HTMLResponse, Response
from fastapi.concurrency import run_in_threadpool
from . import evidence_storage
from pydantic import BaseModel, Field

from .product_database import (
    active_hazards,
    change_password,
    assign_report,
    confirm_report,
    create_community_comment,
    create_community_post,
    create_report_message,
    create_emergency_contact,
    create_journey,
    acknowledge_journey_checkin,
    create_report,
    create_saved_place,
    create_user,
    decide_report,
    deactivate_account,
    delete_emergency_contact,
    delete_saved_place,
    hazard_history,
    duplicate_candidates,
    evidence_record,
    end_journey,
    government_metrics,
    list_community_comments,
    list_community_posts,
    list_report_messages,
    list_emergency_contacts,
    list_journeys,
    list_notifications,
    list_news_signals,
    news_configuration,
    list_reports,
    list_saved_places,
    login,
    set_under_review,
    mark_notification_read,
    review_news_signal,
    report_detail,
    request_report_information,
    revoke_session,
    store_evidence,
    shared_journey,
    schedule_journey_checkin,
    suggest_duplicate_reports,
    update_hazard,
    update_journey_location,
    user_for_token,
    toggle_community_vote,
    update_news_configuration,
)
from .news_intelligence import ingest_configured_feeds
from .news_publication import publish_news
from .live_updates import live_updates
from .product_database import (admin_accounts, admin_account_action, admin_app_settings,
                               admin_update_app_settings, admin_audit_history)


router = APIRouter(prefix="/api/v1", tags=["product"])


@router.websocket("/live")
async def live_map_updates(websocket: WebSocket) -> None:
    await live_updates.connect(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        await live_updates.disconnect(websocket)


class RegisterRequest(BaseModel):
    name: str = Field(min_length=2, max_length=80)
    email: str = Field(min_length=5, max_length=254)
    password: str = Field(min_length=10, max_length=128)


class LoginRequest(BaseModel):
    email: str
    password: str


class AdminAccountAction(BaseModel):
    action: Literal["suspend", "restore", "revoke_sessions", "promote_official", "make_citizen"]
    reason: str = Field(min_length=3, max_length=500)


class AdminAppSettings(BaseModel):
    registration_enabled: bool
    community_posting_enabled: bool
    reason: str = Field(min_length=3, max_length=500)


class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str = Field(min_length=10, max_length=128)


class AccountDeleteRequest(BaseModel):
    password: str


class ReportCreate(BaseModel):
    title: str = Field(min_length=5, max_length=140)
    category_key: str = Field(min_length=2, max_length=50, pattern=r"^[A-Z0-9_]+$")
    description: str = Field(min_length=10, max_length=4000)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    address: str = Field(min_length=3, max_length=500)
    evidence_url: str | None = Field(default=None, max_length=2000)
    anonymous: bool = False
    evidence_ids: list[str] = Field(default_factory=list, max_length=5)
    client_request_id: str | None = Field(default=None, min_length=8, max_length=80)


class TriageRequest(BaseModel):
    reason: str = Field(min_length=3, max_length=1000)


class AssignmentRequest(BaseModel):
    department: str = Field(min_length=2, max_length=100)
    assigned_to: str | None = Field(default=None, max_length=100)
    due_hours: int = Field(default=48, ge=1, le=720)


class InformationRequest(BaseModel):
    message: str = Field(min_length=5, max_length=2000)


class ReportMessageCreate(BaseModel):
    content: str = Field(min_length=1, max_length=2000)
    evidence_id: str | None = Field(default=None, max_length=80)
    client_request_id: str | None = Field(default=None,min_length=8,max_length=100)


class NewsPublishRequest(BaseModel):
    passability: Literal["UNKNOWN", "PASSABLE", "IMPASSABLE"] = "UNKNOWN"
    latitude: float = Field(ge=-90,le=90)
    longitude: float = Field(ge=-180,le=180)
    address: str = Field(min_length=5,max_length=500)
    severity: Literal["LOW","MODERATE","HIGH","CRITICAL"]
    road_status: Literal["CAUTION","UNSAFE","WORK_REQUIRED","CLOSED"]
    verification_note: str = Field(min_length=10,max_length=1500)
    confirmed_current: Literal[True]
    valid_hours: int = Field(default=12,ge=1,le=72)
    no_go_radius_m: float | None = Field(default=None,ge=10,le=500)


class NewsReviewRequest(BaseModel):
    review_status: Literal["RELEVANT", "DISMISSED", "NEEDS_FIELD_CHECK"]


class NewsConfigurationRequest(BaseModel):
    refresh_hours: Literal[12, 24]


class SavedPlaceRequest(BaseModel):
    label: str = Field(min_length=1, max_length=80)
    address: str = Field(min_length=3, max_length=500)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class EmergencyContactRequest(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    phone: str = Field(min_length=5, max_length=30, pattern=r"^[+0-9 ()-]+$")
    relationship: str = Field(min_length=2, max_length=80)


class JourneyStartRequest(BaseModel):
    source_label: str = Field(min_length=1, max_length=500)
    destination_label: str = Field(min_length=1, max_length=500)
    destination_latitude: float | None = Field(default=None, ge=-90, le=90)
    destination_longitude: float | None = Field(default=None, ge=-180, le=180)
    travel_mode: Literal["DRIVING", "WALKING", "CYCLING"]
    route_profile: Literal["FASTEST", "BALANCED", "SAFEST"]
    route_summary: str = Field(min_length=1, max_length=2000)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)


class JourneyLocationRequest(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class JourneyCheckInScheduleRequest(BaseModel):
    interval_minutes: Literal[15, 30, 60, 120] | None = None


class HazardGeometryPoint(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class ReviewDecision(BaseModel):
    passability: Literal["UNKNOWN", "PASSABLE", "IMPASSABLE"] = "UNKNOWN"
    decision: Literal["verify", "reject", "duplicate"]
    severity: Literal["LOW", "MODERATE", "HIGH", "CRITICAL"] | None = None
    road_status: Literal["SAFE", "CAUTION", "UNSAFE", "WORK_REQUIRED", "CLOSED"] | None = None
    hazard_status: Literal["ACTIVE", "MONITORING"] | None = None
    reason: str = Field(min_length=3, max_length=2000)
    linked_hazard_id: str | None = None
    valid_until: str | None = None
    geometry_type: Literal["POINT", "LINESTRING", "POLYGON"] | None = None
    geometry_coordinates: list[HazardGeometryPoint] | None = Field(default=None, min_length=2, max_length=100)
    no_go_radius_m: float | None = Field(default=None, ge=10, le=1000)


class HazardUpdate(BaseModel):
    passability: Literal["UNKNOWN", "PASSABLE", "IMPASSABLE"] | None = None
    severity: Literal["LOW", "MODERATE", "HIGH", "CRITICAL"] | None = None
    road_status: Literal["SAFE", "CAUTION", "UNSAFE", "WORK_REQUIRED", "CLOSED"] | None = None
    status: Literal["ACTIVE", "MONITORING", "RESOLVED", "CLOSED"] | None = None
    reason: str = Field(min_length=3, max_length=2000)
    valid_until: str | None = None
    geometry_type: Literal["POINT", "LINESTRING", "POLYGON"] | None = None
    geometry_coordinates: list[HazardGeometryPoint] | None = Field(default=None, min_length=2, max_length=100)
    no_go_radius_m: float | None = Field(default=None, ge=10, le=1000)


class CommunityPostCreate(BaseModel):
    category: Literal["general", "question", "help", "update", "neighbourhood"] = "general"
    content: str = Field(min_length=3, max_length=1500)
    evidence_id: str | None = Field(default=None, max_length=80)
    client_request_id: str | None = Field(default=None, min_length=16, max_length=80, pattern=r"^[A-Za-z0-9_-]+$")


class CommunityCommentCreate(BaseModel):
    content: str = Field(min_length=2, max_length=1000)
    parent_id: str | None = Field(default=None, max_length=80)
    client_request_id: str | None = Field(default=None, min_length=16, max_length=80, pattern=r"^[A-Za-z0-9_-]+$")


class AssistantTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=2000)


class AssistantRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    history: list[AssistantTurn] = Field(default_factory=list, max_length=12)


def current_user(authorization: Annotated[str | None, Header()] = None) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Bearer token required")
    user = user_for_token(authorization[7:].strip())
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired session")
    return user


def government_user(user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] != "government":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Only safety officials may change government records")
    return user


def operations_reader(user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] not in {"government", "admin"}:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Government role required")
    return user


def admin_user(user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] != "admin":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Administrator role required")
    return user


@router.get("/admin/accounts")
def administrative_accounts(_: Annotated[dict, Depends(admin_user)], q: str = Query(default="", max_length=100)) -> list[dict]:
    return admin_accounts(q)


@router.post("/admin/accounts/{account_id}/action")
def administrative_account_action(account_id: str, request: AdminAccountAction,
                                  user: Annotated[dict, Depends(admin_user)]) -> dict:
    try: result = admin_account_action(user["id"], account_id, request.action, request.reason)
    except ValueError as error: raise HTTPException(409, str(error)) from error
    if result is None: raise HTTPException(404, "Account not found")
    return result


@router.get("/admin/app-settings")
def administrative_settings(_: Annotated[dict, Depends(admin_user)]) -> dict:
    return admin_app_settings()


@router.patch("/admin/app-settings")
def administrative_update_settings(request: AdminAppSettings, user: Annotated[dict, Depends(admin_user)]) -> dict:
    return admin_update_app_settings(user["id"], request.model_dump(exclude={"reason"}), request.reason)


@router.get("/admin/audit")
def administrative_audit(_: Annotated[dict, Depends(admin_user)]) -> list[dict]:
    return admin_audit_history()


@router.post("/auth/register", status_code=status.HTTP_201_CREATED)
def register(request: RegisterRequest) -> dict:
    if not admin_app_settings()["registration_enabled"]:
        raise HTTPException(403, "New registrations are currently paused by the administrator")
    if "@" not in request.email or request.email.startswith("@"):
        raise HTTPException(422, "A valid email address is required")
    try:
        user = create_user(request.name, request.email, request.password)
        authenticated = login(request.email, request.password)
        assert authenticated is not None
        token, _ = authenticated
        return {"access_token": token, "token_type": "bearer", "user": user}
    except ValueError as error:
        raise HTTPException(409, str(error)) from error


@router.post("/auth/login")
def sign_in(request: LoginRequest) -> dict:
    authenticated = login(request.email, request.password)
    if authenticated is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid email or password")
    token, user = authenticated
    return {"access_token": token, "token_type": "bearer", "user": user}


@router.post("/auth/logout")
def logout_session(authorization: Annotated[str | None, Header()] = None) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Bearer token required")
    return {"revoked": revoke_session(authorization[7:].strip())}


@router.post("/account/password")
def update_account_password(request: PasswordChangeRequest, user: Annotated[dict, Depends(current_user)]) -> dict:
    if not change_password(user["id"], request.current_password, request.new_password):
        raise HTTPException(400, "Current password is incorrect")
    return {"changed": True, "sessions_revoked": True}


@router.delete("/account")
def delete_account(request: AccountDeleteRequest, user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] != "citizen": raise HTTPException(403, "Government accounts require controlled offboarding")
    if not deactivate_account(user["id"], request.password): raise HTTPException(400, "Password is incorrect")
    return {"deleted": True, "personal_identity_removed": True}


@router.get("/me")
def me(user: Annotated[dict, Depends(current_user)]) -> dict:
    return user


@router.post("/reports", status_code=status.HTTP_201_CREATED)
def submit_report(request: ReportCreate, user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] != "citizen":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Citizen role required")
    return create_report(user["id"], request.model_dump())


@router.post("/evidence", status_code=status.HTTP_201_CREATED)
async def upload_evidence(
    user: Annotated[dict, Depends(current_user)], file: UploadFile = File(...)
) -> dict:
    if user["role"] not in {"citizen","government"}: raise HTTPException(403, "Citizen or official role required")
    media_type = (file.content_type or "").lower()
    allowed = {"image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm"}
    if media_type not in allowed: raise HTTPException(415, "Use JPEG, PNG, WebP, MP4, or WebM evidence")
    content = await file.read(15 * 1024 * 1024 + 1)
    if len(content) > 15 * 1024 * 1024: raise HTTPException(413, "Evidence must be 15 MB or smaller")
    if not content: raise HTTPException(422, "Evidence file is empty")
    try:
        return await run_in_threadpool(store_evidence, user["id"], file.filename or "evidence", media_type, content)
    except evidence_storage.EvidenceStorageError as error:
        raise HTTPException(503, str(error)) from error


@router.get("/evidence/{evidence_id}")
def download_evidence(evidence_id: str, user: Annotated[dict, Depends(current_user)]) -> Response:
    record = evidence_record(evidence_id, user)
    if record is None: raise HTTPException(404, "Evidence not found")
    if evidence_storage.backend_name() == "local":
        if not os.path.isfile(record["path"]): raise HTTPException(404, "Evidence file is unavailable")
        return FileResponse(record["path"], media_type=record["media_type"], filename=record["original_name"])
    try:
        content = evidence_storage.read(record["storage_key"], expected_size=record["size_bytes"], expected_sha256=record["sha256"])
    except evidence_storage.EvidenceStorageError as error:
        raise HTTPException(503, str(error)) from error
    return Response(content, media_type=record["media_type"], headers={
        "Content-Disposition": "attachment; filename*=UTF-8''" + quote(record["original_name"], safe="")})


@router.get("/reports")
def reports(
    user: Annotated[dict, Depends(current_user)],
    report_status: str | None = Query(default=None, alias="status"),
    category: str | None = Query(default=None, max_length=50),
    department: str | None = Query(default=None, max_length=100),
    assigned_to: str | None = Query(default=None, max_length=100),
    ward: str | None = Query(default=None, max_length=100),
    severity: str | None = Query(default=None, pattern="^(LOW|MODERATE|HIGH|CRITICAL)$"),
    older_than_hours: int | None = Query(default=None, ge=1, le=8760),
    overdue: bool = Query(default=False),
) -> list[dict]:
    return list_reports(user, report_status, category, department, assigned_to, ward, severity, older_than_hours, overdue)


@router.get("/reports/duplicates/suggest")
def suggest_duplicates(
    user: Annotated[dict, Depends(current_user)],
    category: str = Query(min_length=2, max_length=50),
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
) -> list[dict]:
    if user["role"] != "citizen": raise HTTPException(403, "Citizen role required")
    return suggest_duplicate_reports(user["id"], category, latitude, longitude)


@router.get("/reports/{report_id}")
def get_report(report_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    value = report_detail(report_id, user)
    if value is None: raise HTTPException(404, "Report not found")
    return value


@router.get("/reports/{report_id}/messages")
def report_messages(report_id: str, user: Annotated[dict, Depends(current_user)],
                    limit: int = Query(default=50,ge=1,le=100), before_id: str | None = Query(default=None,max_length=80),
                    after_id: str | None = Query(default=None,max_length=80)) -> list[dict]:
    try: values = list_report_messages(report_id, user, limit, before_id, after_id)
    except ValueError as error: raise HTTPException(400,str(error)) from error
    if values is None:
        raise HTTPException(404, "Report not found")
    return values


@router.post("/reports/{report_id}/messages", status_code=status.HTTP_201_CREATED)
def post_report_message(report_id: str, request: ReportMessageCreate,
                        user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] == "admin":
        raise HTTPException(403, "Administrators have read-only access to case conversations")
    try:
        value = create_report_message(report_id, user, request.content, request.evidence_id,request.client_request_id)
    except ValueError as error:
        raise HTTPException(400, str(error)) from error
    if value is None:
        raise HTTPException(404, "Report not found")
    return value


@router.post("/reports/{report_id}/confirm")
def confirm_existing_report(report_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    if user["role"] != "citizen": raise HTTPException(403, "Citizen role required")
    value = confirm_report(report_id, user["id"])
    if value is None: raise HTTPException(404, "Report not found")
    return value


@router.post("/government/reports/{report_id}/triage")
def triage_report(
    report_id: str,
    request: TriageRequest,
    user: Annotated[dict, Depends(government_user)],
) -> dict:
    result = set_under_review(report_id, user["id"], request.reason)
    if result is None:
        raise HTTPException(404, "Report not found")
    return result


@router.post("/government/reports/{report_id}/assign")
def assign_government_report(
    report_id: str, request: AssignmentRequest, user: Annotated[dict, Depends(government_user)]
) -> dict:
    value = assign_report(report_id, user["id"], request.department, request.assigned_to, request.due_hours)
    if value is None: raise HTTPException(404, "Report not found")
    return value


@router.post("/government/reports/{report_id}/request-information")
def request_information(
    report_id: str, request: InformationRequest, user: Annotated[dict, Depends(government_user)]
) -> dict:
    value = request_report_information(report_id, user["id"], request.message)
    if value is None: raise HTTPException(404, "Report not found")
    return value


@router.get("/government/reports/{report_id}/duplicates")
def report_duplicates(report_id: str, _: Annotated[dict, Depends(operations_reader)]) -> list[dict]:
    return duplicate_candidates(report_id)


@router.get("/government/metrics")
def metrics(_: Annotated[dict, Depends(operations_reader)]) -> dict:
    return government_metrics()


@router.post("/government/news/ingest")
def ingest_news(user: Annotated[dict, Depends(operations_reader)]) -> dict:
    # Officials may check when due; only admins can force or change the interval.
    return ingest_configured_feeds(force=user["role"] == "admin")


@router.get("/government/news/settings")
def news_settings(_: Annotated[dict, Depends(admin_user)]) -> dict:
    return news_configuration()


@router.patch("/government/news/settings")
def change_news_settings(
    request: NewsConfigurationRequest, user: Annotated[dict, Depends(admin_user)]
) -> dict:
    return update_news_configuration(request.refresh_hours, user["id"])


@router.get("/government/news-signals")
def news_signals(
    _: Annotated[dict, Depends(operations_reader)],
    review_status: str | None = Query(default=None),
) -> list[dict]:
    return list_news_signals(review_status)


@router.post("/government/news-signals/{signal_id}/review")
def review_news(
    signal_id: str, request: NewsReviewRequest, _: Annotated[dict, Depends(operations_reader)]
) -> dict:
    try: value = review_news_signal(signal_id, request.review_status)
    except ValueError as error: raise HTTPException(409,str(error)) from error
    if value is None: raise HTTPException(404, "News signal not found")
    return value


@router.post("/government/news-signals/{signal_id}/publish")
async def publish_news_hazard(signal_id: str, request: NewsPublishRequest,
                              user: Annotated[dict,Depends(government_user)]) -> dict:
    try: value=await run_in_threadpool(publish_news,signal_id,user,request.model_dump())
    except ValueError as error: raise HTTPException(409,str(error)) from error
    if value is None: raise HTTPException(404,"News signal not found")
    await live_updates.broadcast("hazards_changed")
    return value


@router.post("/government/reports/{report_id}/decision")
async def review_report(
    report_id: str,
    request: ReviewDecision,
    user: Annotated[dict, Depends(government_user)],
) -> dict:
    try:
        result = await run_in_threadpool(decide_report, report_id, user["id"], request.model_dump(exclude_none=True))
    except ValueError as error:
        raise HTTPException(409, str(error)) from error
    if result is None:
        raise HTTPException(404, "Report not found")
    await live_updates.broadcast("hazards_changed")
    return result


@router.patch("/government/hazards/{hazard_id}")
async def change_hazard(
    hazard_id: str,
    request: HazardUpdate,
    user: Annotated[dict, Depends(government_user)],
) -> dict:
    result = await run_in_threadpool(update_hazard, hazard_id, user["id"], request.model_dump(exclude_none=True))
    if result is None:
        raise HTTPException(404, "Hazard not found")
    await live_updates.broadcast("hazards_changed")
    return result


@router.get("/hazards/map")
def map_hazards(
    min_lat: float | None = Query(default=None, ge=-90, le=90),
    min_lng: float | None = Query(default=None, ge=-180, le=180),
    max_lat: float | None = Query(default=None, ge=-90, le=90),
    max_lng: float | None = Query(default=None, ge=-180, le=180),
) -> dict:
    values = (min_lat, min_lng, max_lat, max_lng)
    if any(value is not None for value in values) and not all(value is not None for value in values):
        raise HTTPException(422, "All four map bounds are required")
    bounds = values if all(value is not None for value in values) else None
    if bounds and (min_lat > max_lat or min_lng > max_lng):
        raise HTTPException(422, "Invalid map bounds")
    hazards = active_hazards(bounds)  # type: ignore[arg-type]
    from .crime_labels import crime_labels
    from .hazard_policy import restriction_reason, flood_label
    for hazard in hazards:
        hazard["crime_types"] = crime_labels(hazard)
        hazard["routing_restriction"] = restriction_reason(hazard)
        hazard["routing_blocked"] = hazard["routing_restriction"] is not None
        hazard["passability_label"] = flood_label(hazard)
    return {"hazards": hazards, "count": len(hazards)}


@router.get("/government/hazards/{hazard_id}/history")
def history(hazard_id: str, _: Annotated[dict, Depends(operations_reader)]) -> list[dict]:
    return hazard_history(hazard_id)


@router.get("/notifications")
def notifications(user: Annotated[dict, Depends(current_user)]) -> list[dict]:
    return list_notifications(user["id"])


@router.post("/notifications/{notification_id}/read")
def read_notification(notification_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    if not mark_notification_read(notification_id, user["id"]):
        raise HTTPException(404, "Notification not found")
    return {"id": notification_id, "read": True}


@router.get("/saved-places")
def saved_places(user: Annotated[dict, Depends(current_user)]) -> list[dict]:
    return list_saved_places(user["id"])


@router.post("/saved-places", status_code=status.HTTP_201_CREATED)
def save_place(request: SavedPlaceRequest, user: Annotated[dict, Depends(current_user)]) -> dict:
    return create_saved_place(user["id"], request.model_dump())


@router.delete("/saved-places/{place_id}")
def remove_saved_place(place_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    if not delete_saved_place(user["id"], place_id): raise HTTPException(404, "Saved place not found")
    return {"deleted": True}


@router.get("/emergency-contacts")
def emergency_contacts(user: Annotated[dict, Depends(current_user)]) -> list[dict]:
    return list_emergency_contacts(user["id"])


@router.post("/emergency-contacts", status_code=status.HTTP_201_CREATED)
def add_emergency_contact(request: EmergencyContactRequest, user: Annotated[dict, Depends(current_user)]) -> dict:
    return create_emergency_contact(user["id"], request.model_dump())


@router.delete("/emergency-contacts/{contact_id}")
def remove_emergency_contact(contact_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    if not delete_emergency_contact(user["id"], contact_id): raise HTTPException(404, "Emergency contact not found")
    return {"deleted": True}


@router.get("/journeys")
def journeys(user: Annotated[dict, Depends(current_user)]) -> list[dict]:
    return list_journeys(user["id"])


@router.post("/journeys", status_code=status.HTTP_201_CREATED)
def start_journey(request: JourneyStartRequest, user: Annotated[dict, Depends(current_user)]) -> dict:
    return create_journey(user["id"], request.model_dump())


@router.patch("/journeys/{journey_id}/location")
def update_journey(journey_id: str, request: JourneyLocationRequest,
                   user: Annotated[dict, Depends(current_user)]) -> dict:
    value = update_journey_location(user["id"], journey_id, request.latitude, request.longitude)
    if value is None: raise HTTPException(404, "Active journey not found")
    return value


@router.post("/journeys/{journey_id}/end")
def finish_journey(journey_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    value = end_journey(user["id"], journey_id)
    if value is None: raise HTTPException(404, "Active journey not found")
    return value


@router.post("/journeys/{journey_id}/check-in/schedule")
def schedule_checkin(journey_id: str, request: JourneyCheckInScheduleRequest,
                     user: Annotated[dict, Depends(current_user)]) -> dict:
    value = schedule_journey_checkin(user["id"], journey_id, request.interval_minutes)
    if value is None: raise HTTPException(404, "Active journey not found")
    return value


@router.post("/journeys/{journey_id}/check-in")
def acknowledge_checkin(journey_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    value = acknowledge_journey_checkin(user["id"], journey_id)
    if value is None: raise HTTPException(404, "Active journey not found")
    return value


@router.get("/journeys/share/{share_token}")
def public_shared_journey(share_token: str) -> dict:
    value = shared_journey(share_token)
    if value is None: raise HTTPException(404, "Journey link not found")
    return value


@router.get("/journeys/share/{share_token}/view", response_class=HTMLResponse)
def public_shared_journey_view(share_token: str) -> HTMLResponse:
    value = shared_journey(share_token)
    if value is None: raise HTTPException(404, "Journey link not found")
    destination = html.escape(value["destination_label"]); status_value = html.escape(value["status"])
    updated = html.escape(value["updated_at"]); lat = value.get("last_latitude"); lng = value.get("last_longitude")
    checkin = "Safety check-ins are not scheduled."
    if value.get("next_checkin_at"):
        due = datetime.fromisoformat(value["next_checkin_at"])
        overdue = value["status"] == "ACTIVE" and datetime.now(timezone.utc) > due
        checkin = ("CHECK-IN OVERDUE — please contact the traveller." if overdue else
                   f'Last safety response: {html.escape(value.get("last_checkin_at") or "awaiting first response")}. Next due: {html.escape(value["next_checkin_at"])}.')
    location = "Location has not been shared yet."
    if lat is not None and lng is not None:
        location = f'<a href="https://www.openstreetmap.org/?mlat={float(lat):.6f}&mlon={float(lng):.6f}#map=16/{float(lat):.6f}/{float(lng):.6f}">Open latest location on OpenStreetMap</a>'
    return HTMLResponse(f"""<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Safe-Navi journey</title>
    <style>body{{font-family:system-ui;background:#f4f8f6;color:#0b302c;padding:24px;max-width:620px;margin:auto}}main{{background:white;padding:24px;border-radius:22px;box-shadow:0 8px 28px #003b3320}}b{{color:#008d79}}a{{color:#007a68;font-weight:700}}</style></head>
    <body><main><p><b>SAFE-NAVI · CONSENTED JOURNEY SHARE</b></p><h1>Journey to {destination}</h1><p>Status: {status_value}</p><p>{location}</p><p><strong>{checkin}</strong></p><p>Last updated: {updated}</p><small>This link shows only journey status and the latest consented location. It does not expose account identity or historical movement.</small></main></body></html>""")


@router.get("/community/posts")
def community_posts(
    user: Annotated[dict, Depends(current_user)],
    category: str | None = Query(default=None, max_length=30),
    q: str | None = Query(default=None, max_length=100),
) -> list[dict]:
    return list_community_posts(user["id"], category, q)


@router.post("/community/posts", status_code=status.HTTP_201_CREATED)
def publish_community_post(
    request: CommunityPostCreate, user: Annotated[dict, Depends(current_user)]
) -> dict:
    if not admin_app_settings()["community_posting_enabled"]:
        raise HTTPException(403, "Community posting is currently paused by the administrator")
    try:
        return create_community_post(user["id"], request.category, request.content, request.evidence_id, request.client_request_id)
    except ValueError as error:
        raise HTTPException(400, str(error)) from error


@router.post("/community/posts/{post_id}/vote")
def vote_community_post(post_id: str, user: Annotated[dict, Depends(current_user)]) -> dict:
    post = toggle_community_vote(post_id, user["id"])
    if post is None:
        raise HTTPException(404, "Community post not found")
    return post


@router.get("/community/posts/{post_id}/comments")
def community_comments(post_id: str, _: Annotated[dict, Depends(current_user)]) -> list[dict]:
    return list_community_comments(post_id)


@router.post("/community/posts/{post_id}/comments", status_code=status.HTTP_201_CREATED)
def publish_community_comment(
    post_id: str,
    request: CommunityCommentCreate,
    user: Annotated[dict, Depends(current_user)],
) -> dict:
    if not admin_app_settings()["community_posting_enabled"]:
        raise HTTPException(403, "Community posting is currently paused by the administrator")
    try: comment = create_community_comment(post_id, user["id"], request.content, request.parent_id, request.client_request_id)
    except ValueError as error: raise HTTPException(400, str(error)) from error
    if comment is None:
        raise HTTPException(404, "Community post not found")
    return comment


@router.post("/assistant/chat")
async def assistant_chat(
    request: AssistantRequest, user: Annotated[dict, Depends(current_user)]
) -> dict:
    api_key = os.getenv("GROQ_API_KEY", "").strip()
    if not api_key:
        raise HTTPException(503, "Safe-Navi AI is not configured. Add GROQ_API_KEY to the backend environment.")
    model = os.getenv("GROQ_MODEL", "openai/gpt-oss-20b").strip()
    hazard_count = len(await run_in_threadpool(active_hazards))
    system_prompt = (
        "You are Navi, the in-app Safe-Navi safety assistant. Give concise, calm, practical guidance about "
        "route planning, civic and crime issue reporting, verified hazards, and personal preparedness. "
        "Never claim a place or route is safe, never invent live incidents, and never treat community posts as verified. "
        "Only government-verified hazards may be described as verified. For immediate danger tell the person to move "
        "to a safer public place and contact local emergency services. Do not provide vigilantism, pursuit, or confrontation advice. "
        f"The signed-in person is {user['name']}. Safe-Navi currently exposes {hazard_count} active or monitored verified hazards."
    )
    messages = [{"role": "system", "content": system_prompt}]
    messages.extend(turn.model_dump() for turn in request.history[-10:])
    messages.append({"role": "user", "content": request.message})
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                "https://api.groq.com/openai/v1/chat/completions",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json={"model": model, "messages": messages, "temperature": 0.25, "max_completion_tokens": 600},
            )
        if response.status_code >= 400:
            raise HTTPException(502, "The AI provider is temporarily unavailable.")
        payload = response.json()
        answer = payload["choices"][0]["message"]["content"].strip()
        return {"answer": answer, "provider": "groq", "model": model}
    except HTTPException:
        raise
    except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as error:
        raise HTTPException(502, "The AI provider returned an invalid response.") from error
