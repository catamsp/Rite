# Privacy Policy for Rite

**Last Updated:** July 07, 2026

This Privacy Policy describes how **Rite** ("the Application"), an Android software utility developed by **catamsp / Tamilselvan** ("we," "us," or "our"), handles your data. We respect your privacy and are committed to protecting it through complete transparency.

---

## 1. Information Collection and Use

### A. Core Philosophy: Zero Data Collection
The Application operates entirely client-side. **We do not collect, harvest, store, or transmit any of your personal data, text inputs, or usage metrics to our own servers.** We do not own, lease, or operate any remote databases or servers capable of receiving your data.

### B. Android Accessibility Service API
Rite utilizes Android's **AccessibilityService API** to provide its core functionality.
* **How it is used:** The API is used strictly to listen for text input field events, detect specific user-defined trigger phrases (e.g., `?fix`), and programmatically replace that text inline with the AI-generated response.
* **Scope of access:** The service only processes text within active text fields where a trigger phrase has been actively typed by the user. 
* **Data Transmission:** The text captured via the Accessibility Service is temporarily processed in memory to execute the user's explicit request. It is **never recorded, cached on the device, or transmitted to the developer.**

### C. Third-Party AI Service Integration
To perform intelligent text transformations, the Application securely communicates with third-party Artificial Intelligence providers (such as Google Gemini, Groq, or OpenAI-compatible endpoints) chosen explicitly by the user.
* When you execute an AI command, the text you typed is transmitted directly via HTTPS from your device to the specific AI endpoint you configured.
* Your text interactions with these third parties are subject to their respective privacy policies. We encourage you to read the privacy policies of the AI infrastructure you connect to the Application.

---

## 2. Data Security & Storage

### A. API Key Encryption
The Application requires user-provided API keys to connect to third-party AI services. 
* All API keys are stored exclusively on your local device.
* They are heavily secured and encrypted using **AES-256-GCM** cryptography via the hardware-backed **Android Keystore System**. 
* These keys never leave your device unencrypted and are never accessible to the developer or any unauthorized third-party apps.

### B. Local Data
Any offline text replacer shortcuts or configuration settings you create are stored entirely in local app storage on your device. Clearing the app data or uninstalling the Application completely erases all local data.

---

## 3. Children's Privacy
The Application does not knowingly collect or process data from anyone under the age of 13. Since no data is collected or sent to the developer, the Application is inherently safe regarding children's data privacy under international guidelines.

---

## 4. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by updating the "Last Updated" date at the top of this page. You are advised to review this Privacy Policy periodically for any changes.

---

## 5. Contact Us
If you have any questions, suggestions, or feedback regarding this Privacy Policy, please feel free to open an issue or contact us directly via our public repository:

* **GitHub Repository:** https://github.com/catamsp/rite
