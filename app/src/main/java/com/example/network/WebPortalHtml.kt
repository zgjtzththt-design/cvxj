package com.example.network

object WebPortalHtml {
    fun render(deviceName: String, mode: String, token: String): String {
        val safeName = deviceName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val safeToken = token.replace("\"", "")
        val safeMode = if (mode == "send") "send" else "receive"

        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>DropLink — $safeName</title>
<style>
  :root {
    --bg: #060910;
    --card: #111827;
    --elevated: #182236;
    --border: #23304A;
    --blue: #2E7DFF;
    --cyan: #22D3EE;
    --green: #10B981;
    --red: #F43F5E;
    --text: #F1F5F9;
    --muted: #94A3B8;
  }
  * { box-sizing: border-box; font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif; }
  body {
    margin: 0; padding: 20px; background: radial-gradient(circle at top right, #0E1B33, var(--bg) 55%);
    color: var(--text); min-height: 100vh; display: flex; justify-content: center; align-items: flex-start;
  }
  .container { width: 100%; max-width: 680px; margin-top: 12px; }
  .header {
    display: flex; align-items: center; justify-content: space-between;
    background: var(--card); border: 1px solid var(--border); border-radius: 20px;
    padding: 18px 22px; margin-bottom: 16px;
  }
  .brand { display: flex; align-items: center; gap: 12px; }
  .logo {
    width: 42px; height: 42px; border-radius: 12px;
    background: linear-gradient(135deg, var(--blue), var(--cyan));
    display: flex; align-items: center; justify-content: center; font-weight: 800; color: #04101E; font-size: 18px;
  }
  .badge {
    background: rgba(34,211,238,0.12); color: var(--cyan); border: 1px solid rgba(34,211,238,0.3);
    padding: 5px 12px; border-radius: 999px; font-size: 12px; font-weight: 600;
  }
  .card {
    background: var(--card); border: 1px solid var(--border); border-radius: 20px;
    padding: 22px; margin-bottom: 16px;
  }
  h1 { margin: 0; font-size: 20px; }
  .sub { color: var(--muted); font-size: 13px; margin-top: 4px; }
  .dropzone {
    border: 2px dashed var(--border); border-radius: 18px; padding: 36px 20px; text-align: center;
    cursor: pointer; transition: 0.2s ease; background: rgba(46,125,255,0.03);
  }
  .dropzone.dragover { border-color: var(--cyan); background: rgba(34,211,238,0.08); }
  .btn {
    background: linear-gradient(135deg, var(--blue), #1D5ED6); color: white; border: none;
    border-radius: 14px; padding: 12px 22px; font-size: 14px; font-weight: 600; cursor: pointer;
    display: inline-flex; align-items: center; gap: 8px; text-decoration: none;
  }
  .btn:disabled { opacity: 0.5; cursor: not-allowed; }
  .file-row {
    display: flex; justify-content: space-between; align-items: center; gap: 12px;
    background: var(--elevated); border: 1px solid var(--border); border-radius: 14px;
    padding: 12px 14px; margin-top: 10px;
  }
  .file-name { font-weight: 600; font-size: 14px; word-break: break-all; }
  .file-meta { color: var(--muted); font-size: 12px; margin-top: 2px; }
  .bar-bg { height: 8px; background: #0B111E; border-radius: 999px; overflow: hidden; margin-top: 8px; }
  .bar-fill { height: 100%; width: 0%; background: linear-gradient(90deg, var(--blue), var(--cyan)); transition: width 0.15s; }
  .status-ok { color: var(--green); font-size: 12px; font-weight: 600; }
  .status-err { color: var(--red); font-size: 12px; font-weight: 600; }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <div class="brand">
      <div class="logo">DL</div>
      <div>
        <h1>DropLink — $safeName</h1>
        <div class="sub">نقل محلي مباشر عبر الشبكة المحلية • Local P2P Web Portal</div>
      </div>
    </div>
    <span class="badge">${if (safeMode == "send") "وضع التحميل • Download" else "وضع الاستقبال • Upload"}</span>
  </div>

  <div id="receivePanel" class="card" style="display:${if (safeMode == "receive") "block" else "none"}">
    <div id="dropzone" class="dropzone">
      <div style="font-size:17px;font-weight:700;margin-bottom:6px;">اسحب الملفات هنا أو اضغط للاختيار</div>
      <div class="sub" style="margin-bottom:16px;">Drag & drop files here or click to browse — لا يتم رفع الملفات للإنترنت</div>
      <input id="fileInput" type="file" multiple style="display:none" />
      <button class="btn" type="button" onclick="document.getElementById('fileInput').click()">اختيار ملفات / Choose Files</button>
    </div>
    <div id="uploadList"></div>
  </div>

  <div id="sendPanel" class="card" style="display:${if (safeMode == "send") "block" else "none"}">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <div>
        <div style="font-weight:700;font-size:16px;">الملفات المشاركة من $safeName</div>
        <div class="sub">Shared files ready for direct local download</div>
      </div>
    </div>
    <div id="sharedFilesList">جارٍ التحميل...</div>
  </div>
</div>

<script>
const TOKEN = "$safeToken";
const MODE = "$safeMode";

function fmtBytes(b) {
  if (!b || b <= 0) return "0 B";
  const u = ["B","KB","MB","GB","TB"];
  const i = Math.min(Math.floor(Math.log(b)/Math.log(1024)), u.length - 1);
  return (b / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + " " + u[i];
}

if (MODE === "send") {
  fetch("/api/status?token=" + encodeURIComponent(TOKEN))
    .then(r => r.json())
    .then(data => {
      const box = document.getElementById("sharedFilesList");
      if (!data.files || data.files.length === 0) {
        box.innerHTML = "<div class='sub'>لا توجد ملفات مشاركة حاليًا.</div>";
        return;
      }
      box.innerHTML = "";
      data.files.forEach(f => {
        const row = document.createElement("div");
        row.className = "file-row";
        row.innerHTML = '<div><div class="file-name"></div><div class="file-meta"></div></div>' +
          '<a class="btn" download>تنزيل / Download</a>';
        row.querySelector(".file-name").textContent = f.name;
        row.querySelector(".file-meta").textContent = fmtBytes(f.sizeBytes) + (f.mimeType ? " • " + f.mimeType : "");
        const a = row.querySelector("a");
        a.href = "/api/download/" + f.index + "?token=" + encodeURIComponent(TOKEN);
        box.appendChild(row);
      });
    })
    .catch(() => {
      document.getElementById("sharedFilesList").innerHTML = "<div class='status-err'>انتهت الجلسة أو تعذر الاتصال.</div>";
    });
}

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("fileInput");
const uploadList = document.getElementById("uploadList");

if (dropzone) {
  dropzone.addEventListener("dragover", e => { e.preventDefault(); dropzone.classList.add("dragover"); });
  dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
  dropzone.addEventListener("drop", e => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    handleFiles(e.dataTransfer.files);
  });
  fileInput.addEventListener("change", e => handleFiles(e.target.files));
}

async function handleFiles(fileList) {
  for (const file of fileList) {
    await uploadSingleFile(file);
  }
}

function uploadSingleFile(file) {
  return new Promise((resolve) => {
    const row = document.createElement("div");
    row.className = "file-row";
    row.style.flexDirection = "column";
    row.style.alignItems = "stretch";
    row.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;">' +
        '<div><div class="file-name"></div><div class="file-meta"></div></div>' +
        '<div class="status-text sub">بانتظار الموافقة...</div>' +
      '</div>' +
      '<div class="bar-bg"><div class="bar-fill"></div></div>';
    row.querySelector(".file-name").textContent = file.name;
    row.querySelector(".file-meta").textContent = fmtBytes(file.size);
    uploadList.prepend(row);

    const statusEl = row.querySelector(".status-text");
    const fillEl = row.querySelector(".bar-fill");

    const transferId = "web-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8);
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/upload?token=" + encodeURIComponent(TOKEN), true);
    xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
    xhr.setRequestHeader("X-File-Size", String(file.size));
    xhr.setRequestHeader("X-File-Mime", file.type || "application/octet-stream");
    xhr.setRequestHeader("X-Transfer-Id", transferId);
    xhr.setRequestHeader("X-Sender-Name", encodeURIComponent("Web Browser"));

    const startTime = Date.now();
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const pct = Math.round((e.loaded / e.total) * 100);
        fillEl.style.width = pct + "%";
        const elapsedSec = Math.max((Date.now() - startTime) / 1000, 0.2);
        const bps = e.loaded / elapsedSec;
        statusEl.textContent = pct + "% • " + fmtBytes(bps) + "/s";
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        fillEl.style.width = "100%";
        statusEl.className = "status-ok";
        statusEl.textContent = "تم الإرسال بنجاح ✓";
      } else {
        statusEl.className = "status-err";
        statusEl.textContent = xhr.status === 403 ? "تم الرفض من الجهاز المستقبِل" : "فشل الإرسال (" + xhr.status + ")";
      }
      resolve();
    };
    xhr.onerror = () => {
      statusEl.className = "status-err";
      statusEl.textContent = "انقطع الاتصال";
      resolve();
    };
    xhr.send(file);
  });
}
</script>
</body>
</html>
        """.trimIndent()
    }
}
