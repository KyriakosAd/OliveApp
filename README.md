# Organic Olive Management App [![License](https://img.shields.io/github/license/KyriakosAd/OliveApp.svg)](https://github.com/KyriakosAd/OliveApp/blob/main/LICENSE) [![Size](https://img.shields.io/github/repo-size/KyriakosAd/OliveApp.svg)](https://github.com/KyriakosAd/OliveApp)

A mobile app that manages olive grove locations and alerts workers to avoid spraying nearby organic groves.
* Created on **Android Studio** with **Jetpack Compose**.
* **Google Identity Platform** for user authentication and **Firebase Realtime Database** to store data in the **cloud**.
* **MapLibre Native** to display the map and map tiles from jawg.io.

When an authenticated user (worker) is within:
* **10 meters** of a **non-sprayed** olive grove, it converts it to **sprayed** and updates the **cloud** database.
* **20 meters** of an **organic** olive grove, it sents a notification to alert the worker not to spray that olive grove.

<p align="left">
  &nbsp;&nbsp;&nbsp;
  <img align="middle" src="https://github.com/KyriakosAd/OliveApp/assets/115529039/812312d9-4a42-4caa-82d2-97e951db2e6a" width="24%" />
  &nbsp;&nbsp;&nbsp;
  <img align="middle" src="https://github.com/KyriakosAd/OliveApp/assets/115529039/4a1070d4-91e9-4701-951f-ccd99b05784f" width="24%" /> 
  &nbsp;&nbsp;&nbsp;
  <img align="middle" src="https://github.com/KyriakosAd/OliveApp/assets/115529039/5f0c739e-3ede-47d0-9bd7-1d5535d0d71c" width="24%" /> 
</p>

&nbsp;&nbsp;&nbsp;***Green markers** correspond to **organic** olive groves, **Blue** to **sprayed** and **Red** to **non-sprayed**.*
