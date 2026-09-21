# X Chat Mini — Android floating chat + auto English translation

Ek chhota floating window jo doosre apps ke upar rehta hai. Aap phone me kuch bhi karte raho,
chat saamne rehti hai aur har naye message ke neeche uska English translation blue line me aata hai.

## Kya-kya hai

- Floating overlay window — drag karke kahin bhi rakho, neeche wale handle se resize karo
- Tap "–" se chhoti bubble me simat jata hai; bubble pe naye translate hue messages ka red badge
- Bubble ko drag karo, tap karo to window wapas khul jata hai
- Opacity button: 100% → 85% → 70% → 55% (halka transparent, peeche ka kaam dikhta rahe)
- "EN" button se translation on/off
- Login ek baar, cookies save rehti hain
- 4 free translation engines (Google x2, MyMemory, Lingva) — ek block ho to agla apne aap

## APK kaise banayein (bina Android Studio ke)

1. GitHub pe naya repo banao, is folder ki saari files usme upload kar do
   (web pe: Add file → Upload files → saari files+folders drag karo → Commit)
2. Repo ke **Actions** tab me jao → "Build APK" workflow → wo apne aap chal padega
   (na chale to "Run workflow" dabao)
3. 3-5 minute me green tick aa jayega → us run ko kholo → neeche **Artifacts** me
   `XChatMini-apk` download karo
4. ZIP ke andar `app-debug.apk` hai — phone me bhejo aur install karo
   (Android "unknown sources" ki permission maangega, allow kar dena)

## Phone me pehli baar

1. App kholo → "1. Permission do" → "Display over other apps" ON
2. "2. Floating window kholo"
3. Window me X me login karo → apni group chat kholo
4. Ab koi bhi app use karo, window upar hi rahega

Band karna ho to window ke "✕" se, ya notification me "Band karo" se.

## Note

Translation free public endpoints se hoti hai — koi API key nahi. Bahut tez scroll karne par
kabhi rate-limit lag sakta hai, tab app khud 12 second ruk kar dobara chalu ho jata hai.
Paise/time wale decision sirf translation pe mat lena — slang me machine translation phisal sakti hai.
