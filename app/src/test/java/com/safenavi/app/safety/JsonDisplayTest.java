package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;
import com.safenavi.app.product.JsonDisplay;
import org.json.JSONObject;
import org.junit.Test;

public class JsonDisplayTest {
    @Test public void rendersUnassignedFieldsWithoutLiteralNull() throws Exception {
        JSONObject report = new JSONObject("{\"assigned_department\":null,\"due_at\":null,\"requested_info\":null}");
        assertEquals("Unassigned", JsonDisplay.text(report, "assigned_department", "Unassigned"));
        assertEquals("Not scheduled", JsonDisplay.text(report, "due_at", "Not scheduled"));
        assertEquals("", JsonDisplay.text(report, "requested_info", ""));
        assertEquals("Missing", JsonDisplay.text(report, "unknown", "Missing"));
        report.put("assigned_department", "Street Lighting");
        assertEquals("Street Lighting", JsonDisplay.text(report, "assigned_department", "Unassigned"));
    }
}
