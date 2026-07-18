/*
 * Dutch practice vocabulary.
 *
 * This is your word/phrase database. Edit it freely — add, remove, or
 * regroup items. Each item needs a Dutch string ("nl") and an English
 * string ("en"). "note" is optional (shown as a small hint on the card).
 *
 * NOTE: This is the copy the Android app uses. If you also edit the web
 * version at ../words.js, keep the two in sync (or just edit this one and
 * re-run `npx cap sync android`).
 */
window.VOCAB = {
  translationLang: "en-US",
  dutchLang: "nl-NL",
  groups: [
    {
      id: "greetings",
      name: "Greetings & basics",
      items: [
        { nl: "Hallo", en: "Hello" },
        { nl: "Goedemorgen", en: "Good morning" },
        { nl: "Goedemiddag", en: "Good afternoon" },
        { nl: "Goedenavond", en: "Good evening" },
        { nl: "Tot ziens", en: "Goodbye" },
        { nl: "Tot straks", en: "See you later" },
        { nl: "Alsjeblieft", en: "Please / here you go", note: "informal" },
        { nl: "Dank je wel", en: "Thank you", note: "informal" },
        { nl: "Dank u wel", en: "Thank you", note: "formal" },
        { nl: "Graag gedaan", en: "You're welcome" },
        { nl: "Sorry", en: "Sorry" },
        { nl: "Ja", en: "Yes" },
        { nl: "Nee", en: "No" }
      ]
    },
    {
      id: "getting-by",
      name: "Getting by",
      items: [
        { nl: "Hoe gaat het?", en: "How are you?" },
        { nl: "Het gaat goed, dank je", en: "I'm fine, thank you" },
        { nl: "Hoe heet je?", en: "What's your name?" },
        { nl: "Ik heet ...", en: "My name is ..." },
        { nl: "Aangenaam", en: "Nice to meet you" },
        { nl: "Spreekt u Engels?", en: "Do you speak English?" },
        { nl: "Ik begrijp het niet", en: "I don't understand" },
        { nl: "Kunt u dat herhalen?", en: "Can you repeat that?" },
        { nl: "Langzamer, alstublieft", en: "Slower, please" },
        { nl: "Waar is het toilet?", en: "Where is the toilet?" },
        { nl: "Hoeveel kost het?", en: "How much does it cost?" },
        { nl: "Ik weet het niet", en: "I don't know" }
      ]
    },
    {
      id: "numbers",
      name: "Numbers 1–10",
      items: [
        { nl: "een", en: "one" },
        { nl: "twee", en: "two" },
        { nl: "drie", en: "three" },
        { nl: "vier", en: "four" },
        { nl: "vijf", en: "five" },
        { nl: "zes", en: "six" },
        { nl: "zeven", en: "seven" },
        { nl: "acht", en: "eight" },
        { nl: "negen", en: "nine" },
        { nl: "tien", en: "ten" }
      ]
    },
    {
      id: "days",
      name: "Days of the week",
      items: [
        { nl: "maandag", en: "Monday" },
        { nl: "dinsdag", en: "Tuesday" },
        { nl: "woensdag", en: "Wednesday" },
        { nl: "donderdag", en: "Thursday" },
        { nl: "vrijdag", en: "Friday" },
        { nl: "zaterdag", en: "Saturday" },
        { nl: "zondag", en: "Sunday" }
      ]
    }
  ]
};
