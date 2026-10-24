# A-Peak

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-navy.svg)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Database-Room%20v7-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero%20Telemetry-success.svg)](#privacy--offline-first)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)](#license)

**A-Peak** is an offline-first, private personal finance, savings, bank, and stock investment tracker designed for Android. Built with 100% Kotlin and Jetpack Compose (Material 3), it delivers complete financial visibility without requiring accounts, logins, network connectivity, or external servers.

---

## Highlights & Principles

- **100% Offline & Private**: Zero internet permissions requested, zero network telemetry, no third-party analytic trackers, and no account required. Your financial data never leaves your device.
- **Mathematical Integrity**: All currency balances and transaction values are stored as 64-bit integer minor units (cents) to avoid floating-point rounding inaccuracies.
- **Modern Android Architecture**: Built following MVVM with clean Repository pattern separations (`StocksRepository`, `ExpensesRepository`, `BanksRepository`) and single-purpose ViewModels.
- **Single-Pass UI Computations**: Heavy metrics and timeline calculations run asynchronously on background threads (`OverviewCalc`) and emit immutable `StateFlow` snapshots.
- **Hardware-Backed & Encrypted Backups**: Dual-mode data backups offering PBKDF2 + AES-256-GCM password-encrypted shareable JSON backups and automated daily Android Keystore snapshots.

---

## Core Modules & Features

### 1. Overview Dashboard
- **Net Worth Tracking**: Live aggregate net worth spanning bank balances and stock positions at their latest recorded prices.
- **Asset Allocation**: Dynamic breakdown of liquidity vs. stock investments with visual portfolio share curves.
- **Savings Runway & Growth**: Visual comparison of raw invested capital vs. net growth, projected savings runway, and combined passive income (bank interest + dividend cashflow).
- **Risk Metrics**: High-concentration warnings, best and worst performing assets, and year-over-year performance cards.

### 2. Bank Accounts
- **Account Types**: Support for multiple checking, savings, and term accounts.
- **Transaction Ledger**: Track deposits, withdrawals, and accrued interest history.
- **Interest Projections**: Annual interest yield calculations and year-by-year historical summaries.
- **Visual Analytics**: Interactive balance curves over time per account.

### 3. Stocks & Investment Portfolio
- **Multi-Broker Management**: Support for accounts across different brokerages with dedicated metadata (broker web URL, notes, custom account labels).
- **Holdings & Aggregation**: Holdings view across multiple accounts with options to merge identical tickers into single line items with weighted allocations.
- **Cost-Basis & PnL**: Strict average-cost basis accounting; buy/sell fees automatically factor into cost and net realized gain calculations.
- **Stock Splits Engine**: Retroactively adjusts historical share quantities, average purchase prices, and price curves while keeping original trade records strictly immutable.
- **Dividends Module**: 12-month trailing total, forward dividend projections based on held shares, dividend yield on cost (YoC) vs. market yield, and monthly distribution bars.
- **Price History & Alerts**: Daily price point tracking, stale price indicators (>7 days without price update), and target/stop-loss price threshold notifications.
- **Realized Gains & Tax Export**: Annual realized profit/loss reporting with fee breakdowns and filtered CSV export for trade logs.
- **Centralized Lookups**: Unified lists for brokers, account types, securities, and sectors with cascading rename synchronization.

### 4. Expense & Budgeting
- **Temporal Hierarchy**: Seamless drill-down from multi-year summaries &rarr; single-year overviews &rarr; monthly breakdowns &rarr; individual daily transaction ledgers.
- **Category Budgets**: Configurable monthly budget limits per category featuring progressive color alerts (amber at 80% usage, red at 100%).
- **Trend Charts**: Monthly stacked category spending charts (Top 5 + other categories) to identify spending patterns.
- **Category Management**: Add, rename, and delete categories with one-click historical transaction updates.

---

## Security & Backups

A-Peak features enterprise-grade local data protection and portable backups:

- **Encrypted Sharing**: Export backups encrypted via **AES-256-GCM** with keys derived using **PBKDF2-HMAC-SHA256** (600,000 iterations, cryptographically random salt and IV). The authenticated header validates password correctness before decryption.
- **Automatic Daily Snapshots**: Rotating daily local backups (retaining the 7 most recent days) stored in private internal storage and encrypted using hardware-backed **Android Keystore** keys.
- **Restoration Modes**:
  - **Merge**: Intelligently reconciles backup data with local tables using primary keys and names, re-parenting child records while keeping existing settings intact.
  - **Replace**: Automatically produces an encrypted safety fallback backup, wipes all tables, and completely restores database state and settings.
- **Backup Verification**: Schema versioning hooks prevent downgrading or importing backups from newer, unsupported app versions.

---

## Technical Specifications

| Parameter | Specification |
| :--- | :--- |
| **Target SDK** | Android 14 (API 34) |
| **Minimum SDK** | Android 8.0 (API 26) |
| **Language** | Kotlin 2.0.x / JVM 17 |
| **UI Toolkit** | Jetpack Compose (BOM 2024.09.00) with Material 3 |
| **Architecture** | MVVM + Repository Pattern + Kotlin Coroutines & StateFlow |
| **Local Database** | Room 2.6.x with KSP, Schema v7 (Automated migrations 1 &rarr; 7) |
| **Icons & Assets** | Custom Canvas vector outline icons (`ui/Icons.kt`) |
| **Application ID** | `com.aajli.apeak` |

---

## Database Schema & Migrations

The SQLite database is managed via Room with strict schema version tracking (`app/schemas/`):
- **v1**: Core transactions, accounts, categories.
- **v2**: Bank accounts, entries, stock accounts, trades, dividends.
- **v3**: Monthly category budgets.
- **v4**: Historical stock price points (`price_points`).
- **v5**: Stock splits engine (`stock_splits`) and stock targets/stop thresholds.
- **v6**: Lookup lists for reusable brokers, securities, and accounts (`lookup_items`, `securities`).
- **v7**: Extended sector details, custom metadata notes, and cascading synchronization.

---

## Architecture Overview

```
app/src/main/java/com/aajli/apeak/
├── MainActivity.kt          # Single-activity Compose entry point & intent handler
├── data/                    # Room Database, DAOs, Entity definitions & Repositories
│   ├── Data.kt              # AppDatabase, Entities, Migrations (1 to 7)
│   ├── StocksRepository.kt  # Stock transactions, splits, dividends & lookups
│   ├── ExpensesRepository.kt# Expense transactions, categories & budgets
│   └── BanksRepository.kt   # Bank accounts & interest entries
├── backup/                  # Cryptography, Keystore & JSON codec
│   ├── BackupCodec.kt       # JSON serialization/deserialization & migrations
│   ├── BackupCrypto.kt      # PBKDF2 key derivation & AES-256-GCM cipher
│   ├── BackupManager.kt     # Backup coordinator & daily housekeeping
│   └── KeystoreKey.kt       # Hardware-backed Android Keystore key management
├── ui/                      # Jetpack Compose UI Screens & Material 3 components
│   ├── Home.kt              # Root navigation & tab bar
│   ├── StocksTab.kt         # Portfolio summary, quick actions & holdings
│   ├── Monthly.kt           # Expense hierarchy (Years, Year, Month, Ledger)
│   ├── Wealth.kt            # Bank accounts and balance visualizations
│   ├── Icons.kt             # Canvas-drawn vector outline icons
│   └── Theme.kt             # Navy/Blue/Slate brand palette & dynamic color
└── util/                    # Pure domain math, calculations & analytics
    ├── OverviewCalc.kt      # Single-pass background metric computation
    ├── Splits.kt            # Stock split adjustment logic
    ├── PriceHistory.kt      # Price point timeline interpolation
    └── Budgets.kt           # Category budget alerting & threshold checks
```

---

## Build & Development

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Building
Open the project directory in Android Studio and let Gradle sync dependencies, or run:
```bash
./gradlew assembleDebug
```

### Running Unit Tests
A comprehensive test suite covers business logic, calculations, migrations, and cryptography:
```bash
./gradlew test
```

---

## Author & License

Developed by **AAJLI Abdallah** ([@aajli-Abdallah](https://github.com/aajli-Abdallah)).
All rights reserved.
