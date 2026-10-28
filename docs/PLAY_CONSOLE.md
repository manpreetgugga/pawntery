# Put Pawtillery on Google Play

A step-by-step list. Parts 1, 2 and 3 can only be done by you (they need your Google account, your ID and a payment). Everything the forms ask for is written out below, ready to copy.

**You need:** a Google account, **$25** (one-time), a photo ID, a phone number, an Android phone, and an email address players can write to. Filling everything in takes about an hour. Google then reviews the app, usually in a few days.

> **Good to know (new personal accounts):** Google requires a **closed test with at least 12 testers for 14 days in a row** before the app can be published to everyone. Part 6 sets that up. Ask family and friends with Android phones early.

---

## Part 1: Create your developer account (only you)

1. Open <https://play.google.com/console/signup> (a computer is easiest).
2. Sign in with the Google account that will own the game.
3. Choose **Yourself** (a personal account).
4. Fill in your name, address, phone number and a contact email. Pay the **$25** fee.
5. Verify your identity: Google asks for a photo of your ID. Approval can take a few days.
6. Google also asks you to confirm you have an Android phone, using the **Play Console** app on it.

## Part 2: Set up the upload key (5 minutes)

Every game file you upload to Google Play is signed with your **upload key**. You received it from Claude as two private files: `pawtillery-upload-key.jks` and `pawtillery-upload-key-SECRETS.txt`.

1. **Save both files somewhere safe**, for example your Google Drive. Never put them in the GitHub repository and never send them to anyone.
2. Give the key to GitHub, so GitHub can build the signed file for you. Open <https://github.com/manpreetgugga/pawntery/settings/secrets/actions>, then **New repository secret**, four times, with the names and values from `pawtillery-upload-key-SECRETS.txt`:
   - `UPLOAD_KEY_ALIAS`
   - `UPLOAD_KEY_PASSWORD`
   - `UPLOAD_KEYSTORE_PASSWORD`
   - `UPLOAD_KEYSTORE_BASE64` (the long line; copy all of it)
3. Build the Google Play file: open <https://github.com/manpreetgugga/pawntery/actions/workflows/play-bundle.yml>, tap **Run workflow**, then **Run workflow** again. After about 3 minutes, the file `pawtillery-play.aab` is on the **Google Play bundle** release: <https://github.com/manpreetgugga/pawntery/releases/tag/play-bundle>. (Or ask Claude to run it.)

If you ever lose the key, Google can reset it (Play Console > *Test and release > Setup > App signing*), because Google keeps the real app signing key. A reset takes a few days.

## Part 3: Create the app

In Play Console, tap **Create app** and fill in:

| Field | Answer |
|---|---|
| App name | `Pawtillery: Backyard Pet Splat` |
| Default language | English (United States) |
| App or game | **Game** |
| Free or paid | **Free** |
| Declarations | Tick both (Developer Program Policies, US export laws) |

Then **Create app**.

## Part 4: "Set up your app" (on the app's Dashboard)

Answer each task like this. They describe the game **as it is now**: no ads and no purchases switched on yet. When you switch on Plus or the rewarded ad (docs/MONETIZATION.md), come back and update *Ads*, *Content rating* (digital purchases) and *Data safety*.

| Task | Answer |
|---|---|
| **Privacy policy** | `https://github.com/manpreetgugga/pawntery/blob/main/docs/privacy-policy.md` (first put your support email in that file, at the bottom) |
| **App access** | All functionality is available without any special access |
| **Ads** | No, my app does not contain ads |
| **Content rating** | Start questionnaire. Email: yours. Category: **Game**. Answer **No** to every question: violence, fear, sexuality, gambling, language, drugs, crude humor, users interacting or sharing content, sharing location, buying digital goods. (Pets only get messy with mud, water and paint; nobody gets hurt.) Expected rating: *Everyone* / PEGI 3. |
| **Target audience and content** | Age groups: **6–8, 9–12, 13–15, 16–17 and 18 and over** (kids and grown-ups). Choosing ages under 13 puts the game in Google's **Families** program. That is fine: there are no ads, no accounts and no data collection, and links out of the game are behind a grown-up check. |
| **Data safety** | Does your app collect or share any of the required user data types? **No.** (The Google Play version is fully offline. Only the GitHub test versions check GitHub for updates, and those aren't on Play.) |
| **Advertising ID** | No, my app does not use advertising ID |
| **Government apps** | No |
| **Financial features** | My app doesn't provide any financial features |
| **Health** | My app doesn't have any health features |
| **News app** | No |

## Part 5: Store listing

**Main store listing** (search "Main store listing" in Play Console):

| Field | Where to get it |
|---|---|
| App name, short description, full description | Copy from [store/listing.md](../store/listing.md) |
| App icon (512 × 512) | [store/icon-512.png](../store/icon-512.png) |
| Feature graphic (1024 × 500) | [store/feature-graphic-1024x500.png](../store/feature-graphic-1024x500.png) |
| Phone screenshots | The 8 files in [store/screenshots/](../store/screenshots/), in order |

**Store settings** (search "Store settings"): category **Game > Casual**; tags *Casual, Family, Physics, Turn-based, Offline*; contact email: yours.

## Part 6: Closed test with 12 testers (14 days)

1. *Test and release > Testing > Closed testing*: open the **Alpha** track (or create one).
2. **Countries / regions**: add the countries your testers live in (or all).
3. **Testers**: create an email list with **at least 12** Gmail addresses of people with Android phones. Save. Copy the **opt-in link** and send it to them: they tap it, join, then install Pawtillery from Google Play.
4. **Create new release**: accept **Play App Signing** if asked (Google keeps the app signing key safe; you keep the upload key). Upload `pawtillery-play.aab` from Part 2. Release notes:
   ```
   <en-US>
   First test! Build forts, smash them, splat pets, unlock all five pets and beat twelve Trick Shots.
   </en-US>
   ```
   Then **Next > Save > Send for review** (or *Start rollout to Alpha*).
5. Testers must **stay opted in for 14 days in a row**. Ask them to play a few times and send feedback.

## Part 7: Publish to everyone

1. After 14 days, the Dashboard shows **Apply for production**. Answer Google's short questions about the test (what testers said, what you changed).
2. Once approved: *Production > Create new release*, upload the newest `pawtillery-play.aab`, add release notes from [store/listing.md](../store/listing.md), **Send for review**.

## Every update after that

1. Push the change to `main` (or ask Claude).
2. Run the **Play Store bundle** workflow again: each run gets a higher version code automatically, which Google Play requires.
3. In Play Console, create a new release on the track and upload the new `pawtillery-play.aab`.

Google Play then updates everyone's game automatically. The GitHub test versions keep updating themselves separately. The two are signed differently, so on one phone, uninstall the test version before installing from Google Play.
