# [![Buy Me a Coffee](https://i.imgur.com/rlatSuk.png)](https://www.buymeacoffee.com/galmitrani1)
# Parking CRM Management System

## Introduction

**Parking CRM Management System** is a Java-based, full-stack application designed to manage automated parking operations. It enables subscribers to reserve, drop off, and retrieve vehicles, while assistants and administrators monitor and manage real-time activity, generate visual reports, and handle parking logistics — all with a professional dark-mode UI and robust server-side architecture.

## Features

### 🧍 Subscriber Portal

- **QR-Based Access**: Login and identify using a personal QR code (powered by [ZXing](https://github.com/zxing/zxing)).
- **Vehicle Drop-Off**: Scan QR at the terminal, receive parking code, and leave the car.
- **Vehicle Pickup**: Enter the code to retrieve the car. Lost code recovery via email/SMS.
- **Smart Reservations**: Book parking spots at least 24 hours and up to 7 days in advance with availability logic and auto-expiration.
- **Activity History**: View previous parking sessions and update contact details.
- **Dark Mode**: All interfaces use a sleek, night-friendly UI theme.

### 🅿️ Assistant Interface

- **Live Monitoring**: View all parked cars, user sessions, and parking durations.
- **Subscriber Management**: Search and inspect users and history.
- **Dark Mode**: Consistent dark theme across all screens.

### 📊 Manager Dashboard

- **Visual Analytics**: Auto-generated monthly reports of usage, delays, and status changes.
- **CSV Export**: Download raw and aggregated data.
- **Dark Mode**: Professional dark styling for prolonged usage.

### 🖥️ Automated Server (with GUI)

- **Connection Log Viewer**: Live view of all client-server connections, with active/disconnected filtering.
- **Log Export**: Export full logs to CSV for analysis.
- **Notification API**: Sends order updates (e.g., delays) via Discord webhook or other APIs.
- **Mailing System**: Email/SMS simulation for password recovery and notifications.
- **Singleton Pattern**: Core modules like DB handler and logger follow Singleton architecture.
- **Crash Handling**: Central exception logging and graceful shutdown logic for fault tolerance.
- **OCSF-Based Architecture**: Reliable Java client-server framework.

## Client-Server Communication

All communication between the client and server is performed over TCP using OCSF sockets.
Messages are encoded and decoded as structured JSON objects using [Google Gson](https://github.com/google/gson), allowing easy command routing and response parsing.

## Included Project Files

This repository includes everything needed to run and test the system:

- ✅ `Park_CRM/` – Full Java source code including `client`, `server`, and `shared` modules.
- ✅ `Park_Jar/` – Compiled runnable JARs for the system.
- ✅ `Park_DB/` – Full MySQL schema and table dump (run as .sql script).
- ✅ `TestingScriptsDB/` – Additional SQL scripts to modify the DB for testing.
- ✅ `Park_JavaDoc/` – Auto-generated Javadoc for all classes.
- ✅ `README.md` – This file, with detailed instructions.

## Prerequisites

1. **BellSoft Liberica JDK** [Bellsoft](https://bell-sw.com/pages/downloads/#jdk-24).
2. **Java IDE** [Eclipse](https://www.eclipse.org/downloads/).
3. **MySQL Workbench** [MySQL](https://dev.mysql.com/downloads/workbench/).

## How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/Tapuz97/parking-lot-crm.git
   ```
3. Open the project in your preferred Java IDE (Eclipse/IntelliJ).
4. Import `parking_db.sql` using MySQL Workbench or Terminal (if promted, password is Aa123456)
```bash
 mysql -u root -p < path/to/your_script.sql
 ``` 
6. Launch the server (`Park_Server.java`).
7. Launch the client (`Park_Client.java`) on the same or different machine.
8. Log in as a subscriber/Admin. (user: Admin password: admin)

> **⚠️**: Ensure the MySQL connection string and ports are configured correctly. (Default prefill 127.0.0.1:3306 i,e localhost)


## Contribution

I welcome contributions! Here's how you can help:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/your-feature-name`).
3. Commit your changes (`git commit -am 'Add awesome feature'`).
4. Push to the branch (`git push origin feature/your-feature-name`).
5. Create a Pull Request – I'll review it ASAP.

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [ZXing](https://github.com/zxing/zxing) – QR code support.
- [Google Gson](https://github.com/google/gson) – JSON serialization/deserialization.
- [OCSF Framework](https://github.com/GalMitrani/OCSF-Gal) – Java socket communication.
- Discord API – For webhook-based notifications.

---
