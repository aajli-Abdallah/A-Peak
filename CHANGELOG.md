# Changelog

## 1.3.61
- Stocks: brokers, account names, stocks and sectors are entries with their own details (broker website and note, account note, stock sector and note), entered once in the Lists screen (new Sectors tab).
- Sector is now a pick-or-add list in the stock form; picking a stock fills its sector; picking a broker shows its details.
- Renaming a list entry can update every account or stock that uses it (checkbox, on by default). Changing a stock's symbol, name or sector can update its holdings.
- Room schema v7, migration 6 to 7 (new columns; existing sectors become list entries). Details are part of backups; old backups still import.
- Tests: two weak tests cleaned, new tests for the details in backups and merge.

## 1.3.60
- Architecture step 2, no behavior change: MainViewModel is gone. Repositories for expenses and banks; view models per area (Expenses, Banks, Stocks, Backup, Overview).
- Overview: all numbers are computed once per data change on a background thread (OverviewCalc, unit tested) instead of on every recomposition; the page shows nothing until the first result is ready.
- BackupViewModel now owns export/import and the start-up backup housekeeping.
- Decision recorded: no SQL aggregates for totals (timezone-fragile, not needed yet).

## 1.3.59
- Architecture step 1, no behavior change: new StocksRepository (single source of truth for stock data and its rules) and StocksViewModel for all stock screens. MainViewModel no longer holds stock logic.
- SettingsStore and BackupManager became shared singletons.
- Next: 1.3.60 moves the remaining areas (banks, expenses and budgets, backup, overview) the same way.

## 1.3.58
- Encrypted backups: choose a password when sharing; AES-256-GCM with a PBKDF2 key. Import asks for the password (wrong password or damaged file is refused). Plain backups still import, with a warning.
- Automatic daily copies and the safety copy before Replace are now encrypted with an Android Keystore key. Older local copies are encrypted at the next start.
- Shared files in the cache are deleted at start and before each share. CSV export shows a not-encrypted warning.
- Unit tests for the encryption (round trip, wrong password, modified header and data, key path). DEFERRED.md tracks the cleanup to do after 1.3.60.

## 1.3.56
- Stocks tab redesigned: one scrolling page with a hero card (value, return pill, trend line) and round quick tiles (Prices with a stale badge, Dividends, Gains, Lists) instead of rows of buttons.
- Add a stock straight from the ＋ Stock button: choose the account from a list (or add a new account on the spot) and the stock from a list (or add a new one). Duplicate stock in the same account is refused.
- Stock account dialog: account name and broker are chosen from lists with a "＋ Add new…" entry.
- Lists screen to manage brokers, account names and stocks. New tables lookup_items and securities, filled from existing data by migration 5 to 6; included in backups.
- Unit tests for the lists in backups and merge.

## 1.3.55 (includes the planned 1.3.53 and 1.3.54)
- Realized gains report: each sale with profit/loss, totals and fees by year, closed positions list.
- CSV export of trades and dividends (all years or one year), shared like a backup.
- Stock splits (for example 2-for-1): past quantities, average price, price charts and values adjust automatically; trades you entered stay unchanged. The sell check uses the shares held on the sale date.
- Per stock: sector or tag with a sector chart, note, target price and stop price. Alert when a saved price reaches them, plus a banner on the Stocks tab.
- Room schema v5 with one migration 4 to 5. Splits and the new stock fields are in backups (old backups still import). Unit tests for splits, reports, CSV and alerts.

## 1.3.52
- Dividends screen (Stocks tab > Dividends): last 12 months total, monthly bars, projected yearly dividends, yield on cost and on current value, totals by year and by stock.
- Faster dividend entry: type the dividend per share and the total is filled from the shares held on that date (override allowed).
- No database change. Unit tests for dividend statistics.

## 1.3.51
- Portfolio value vs money invested chart over time: all stocks (Holdings tab) and per account (account page).
- Merge the same ticker across accounts into one line. Weight of each holding in the portfolio.
- Portfolio stats: open holdings, best and worst performer, total fees paid.
- No database change. Unit tests for merging, best/worst and the portfolio series.

## 1.3.50
- Stocks tab: Update prices screen (all held stocks at once, previous price, % change, age badge) and a banner when prices are 7+ days old.
- Stocks tab: Holdings list across all accounts with sorting (value, return, A-Z) and a switch to hide closed positions.
- No database change. Unit tests for holdings and sorting.

## 1.3.0
- Category trend chart on the Year page: spending per month stacked by category (top 5 + other).
- Price history (new table): net worth over time now uses recorded stock prices, and each stock page has a price chart.
- Room schema v4, migration 3 to 4 (backfills history from existing trades and last prices). Price history included in backups.
- Unit tests for category trend, price history, net worth with prices, and backup of prices.
- Already delivered earlier and not rebuilt: spending by category, monthly trend, balance curve, net worth chart.

## 1.2.0
- Monthly budgets per category with progress bars (amber from 80%, red from 100%) and alerts when saving an expense reaches 80% or 100%.
- Budgets screen (set, change, remove; month navigation), budget card on the month page, budget summary and alerts on the Expense tab.
- Limits are versioned by month, so past months keep their old limit.
- Room schema v3 with migration 2 to 3. Budgets included in backups (old backups still import). Unit tests for budget logic.

## 1.1.11
- Add from a month page now starts the calendar in that month (today if it is the current month).
- Expense tab: totals per category for each year (expense and income, all categories listed), a categories-by-year table across years, and a shortcut to add, rename or delete categories.
- Renaming a category can update past transactions too.
- No database schema change. Unit tests for category totals.

## 1.1.10
- Backup and restore for all tables: export and share, import with preview (new/updated/unchanged), Merge or Replace, automatic daily local backup (last 7), share reminder, open backups from WhatsApp or files. `appVersion`, `author`, upgrades hook.
- Monthly tab renamed Expense.
- Expense years page: year-by-year bars, all-years numbers (savings rate, average spending, best year, highest spending year).
- Year page: averages, year forecast, biggest expenses, categories vs last year, spending by weekday, spending by account.
- Overview per-year cards: comparison with the previous year.
- No database change. Unit tests for version compare, merge, backup format, yearly stats.

## 1.0.14
- Month page: comparison with the previous month, end-of-month forecast (current month), day-by-day spending bars with average per day and biggest expenses, category breakdown with change vs last month.
- Year page: comparison with the previous year, balance curve with best and worst month.
- Pure logic in `CashStats` with unit tests. No database change.

## 1.0.13
- Overview statistics: net worth over time, your money vs growth, savings runway, passive income (interest + dividends), stock performance with dividend yield, concentration warning.
- Pure logic in `OverviewStats` with unit tests. No database change.

## 1.0.12
- Monthly tab now opens on a years page. Tap a year for its statistics (totals, income/expense bars per month, savings rate, average spending, spending by category, each month). Tap a month for the month page.
- Month page gets a back button when opened this way. No database change.
- Unit test for year/month totals.

## 1.0.11
- New look: navy/blue/gray/white palette, rounder shapes, navy gradient hero cards with a faint peak shape.
- Custom outline icons (drawn with Canvas) replace emoji in navigation, headers, buttons and history rows. Icon tiles on lists.
- Launcher icon recolored: navy background, sky-blue bars.
- Dynamic colors now default to off (turn on in Settings).

## 1.0.1
- New concept: savings tracker. Bottom navigation: Overview, Banks, Stocks, Monthly.
- Bank accounts (current and savings with yearly rate): deposits, withdrawals, interest, balance chart, history, interest by year.
- Stock accounts: stocks, buys, sells, fees, dividends, average cost, profit/loss, history.
- Overview: net worth, allocation, gains and per-year stats of everything combined.
- Room schema v2 with migration 1 to 2 (monthly data kept). Unit tests for position math.

## 1.0.0
- First version: transactions, accounts, categories, monthly totals.
- Settings (currency, theme, dynamic colors), About with copyright and links.
- Peak-A adaptive icon with monochrome layer.
- Room schema v1 with exportSchema, unit test for money parsing.
