# 🎞️ 90s Negative to Colour Converter — Spring Boot Application

A Spring Boot application that converts **scanned 90s colour film negatives** (C-41 process) into natural-looking positive colour images. Includes a REST API and an interactive drag-and-drop web interface.

---

## ✨ Features

| Feature | Detail |
|---|---|
| **3 Conversion Modes** | Simple Invert, Orange Mask Removal, Auto Color Correct |
| **Orange Mask Compensation** | Detects the C-41 orange base from the film rebate automatically |
| **Auto Levels** | Per-channel histogram stretch for full contrast |
| **Gamma Correction** | sRGB γ = 2.2 for natural display rendering |
| **REST API** | Upload via POST, get PNG bytes or Base64 JSON |
| **Web UI** | Drag-and-drop, side-by-side preview, one-click download |
| **Formats Supported** | JPEG, PNG, TIFF, BMP, WebP (up to 50 MB) |

---

## 🧪 How It Works

### Why 90s Negatives Look Orange
Colour print film (C-41 process, used in virtually all consumer 90s colour film rolls) has an **orange/amber base layer** called the _mask_. Its purpose is technical (to improve colour accuracy in optical printing), but when you scan the negative digitally, the mask must be removed before the colours make sense.

### Conversion Pipeline
```
Raw Negative Scan
       │
       ▼
  [1] Detect Orange Mask
      Sample the unexposed film border (rebate) → measure R, G, B mask offsets
       │
       ▼
  [2] Invert with Mask Compensation
      positive = (255 - negative) × (255 / (255 - mask))
       │
       ▼
  [3] Auto Levels (Auto Color Correct mode only)
      Stretch histogram per channel to [0, 255]
       │
       ▼
  [4] Gamma Correction (Auto Color Correct mode only)
      Apply sRGB γ = 2.2 for natural on-screen rendering
       │
       ▼
  Positive Colour Image (PNG)
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Run the Application

```bash
cd "C:\Users\THEJAAVIGNA RAAJAN\.gemini\antigravity\scratch\negative-to-color"
mvn spring-boot:run
```

Then open **http://localhost:8080** in your browser.

### Run Tests
```bash
mvn test
```

---

## 📡 REST API Reference

### `POST /api/convert/image`
Converts a negative and returns the PNG as a downloadable file.

| Parameter | Type | Description |
|---|---|---|
| `file` | multipart | The negative image (JPEG, PNG, TIFF, BMP, WebP) |
| `mode` | string | `SIMPLE_INVERT` / `ORANGE_MASK_REMOVAL` / `AUTO_COLOR_CORRECT` (default) |

**Example (curl):**
```bash
curl -X POST http://localhost:8080/api/convert/image \
  -F "file=@my_negative.jpg" \
  -F "mode=AUTO_COLOR_CORRECT" \
  --output positive.png
```

---

### `POST /api/convert/image/base64`
Same as above, but returns a JSON envelope with Base64-encoded PNG and metadata.

**Example Response:**
```json
{
  "metadata": {
    "originalFilename": "my_negative.jpg",
    "outputFilename": "my_negative_positive_auto_color_correct.png",
    "modeUsed": "AUTO_COLOR_CORRECT",
    "widthPx": 3024,
    "heightPx": 2016,
    "processingTimeMs": 312,
    "message": "Conversion successful"
  },
  "image": "data:image/png;base64,iVBORw0KGgo...",
  "timestamp": "2026-11-10T17:22:00Z"
}
```

---

### `GET /api/convert/modes`
Returns the list of available conversion modes and descriptions.

---

## 📂 Project Structure

```
src/main/java/com/example/negativetcolor/
├── NegativeToColorApplication.java       # Spring Boot entry point
├── model/
│   ├── ConversionMode.java               # Enum: SIMPLE_INVERT | ORANGE_MASK_REMOVAL | AUTO_COLOR_CORRECT
│   └── ConversionResult.java             # Response metadata DTO
├── service/
│   └── NegativeConversionService.java    # Core image processing logic
└── controller/
    ├── ConversionController.java          # REST endpoints
    └── GlobalExceptionHandler.java        # Error handling
src/main/resources/
├── static/index.html                      # Interactive web UI
└── application.properties
```
