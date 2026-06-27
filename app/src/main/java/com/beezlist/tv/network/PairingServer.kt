package com.beezlist.tv.network

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response

/** Local-only HTTP server: serves a form on GET, hands the submitted playlist URL to [onUrlReceived] on POST. */
class PairingServer(
    private val onUrlReceived: (String) -> Unit,
) : NanoHTTPD(0) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            return try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val url = session.parms["url"]?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    onUrlReceived(url)
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", SUCCESS_HTML)
                } else {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing url")
                }
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error")
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", FORM_HTML)
    }

    companion object {
        private const val FORM_HTML = """
            <html dir="rtl" lang="he"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>BeezList</title></head>
            <body style="font-family:sans-serif;background:#101830;color:#f5f7fa;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0">
            <form method="POST" style="text-align:center;width:90%;max-width:420px">
              <h2 style="color:#FFC400;margin-bottom:4px">BeezList</h2>
              <p>הדביקו כתובת פלייליסט M3U/M3U8</p>
              <input name="url" type="url" autofocus required placeholder="https://example.com/playlist.m3u8"
                     style="width:100%;padding:12px;font-size:16px;border-radius:8px;border:none;margin-bottom:12px;box-sizing:border-box">
              <button type="submit"
                      style="width:100%;padding:12px;font-size:16px;border-radius:8px;border:none;background:#FFC400;color:#18140A;font-weight:bold">
                שלח לטלוויזיה
              </button>
            </form>
            </body></html>
        """

        private const val SUCCESS_HTML = """
            <html dir="rtl" lang="he"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="font-family:sans-serif;background:#101830;color:#f5f7fa;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;text-align:center">
            <div><h2 style="color:#00E0C4">נשלח בהצלחה!</h2><p>אפשר לסגור את הדף ולחזור לטלוויזיה.</p></div>
            </body></html>
        """
    }
}
