package com.maoneart.ioskeyboard.emoji

data class AnimatedEmojiItem(
    val fileName: String,
    val unicode: String,
    val name: String
)

object AnimatedEmojiManager {
    val items = listOf(
        AnimatedEmojiItem("joy.webp", "😂", "Ketawa Air Mata"),
        AnimatedEmojiItem("crying.webp", "😭", "Nangis Kencang"),
        AnimatedEmojiItem("rofl.webp", "🤣", "Ngakak Guling"),
        AnimatedEmojiItem("hearts_smile.webp", "🥰", "Senyum Cinta"),
        AnimatedEmojiItem("kiss.webp", "😘", "Cium"),
        AnimatedEmojiItem("party.webp", "🥳", "Pesta"),
        AnimatedEmojiItem("fire.webp", "🔥", "Api Membara"),
        AnimatedEmojiItem("heart.webp", "❤️", "Hati Merah"),
        AnimatedEmojiItem("broken_heart.webp", "💔", "Patah Hati"),
        AnimatedEmojiItem("heart_fire.webp", "❤️‍🔥", "Cinta Membara"),
        AnimatedEmojiItem("thumbs_up.webp", "👍", "Jempol Mantap"),
        AnimatedEmojiItem("100.webp", "💯", "Seratus"),
        AnimatedEmojiItem("sparkles.webp", "✨", "Kilau Bintang"),
        AnimatedEmojiItem("sunglasses.webp", "😎", "Keren"),
        AnimatedEmojiItem("thinking.webp", "🤔", "Mikir"),
        AnimatedEmojiItem("exploding.webp", "🤯", "Pikiran Meledak"),
        AnimatedEmojiItem("pleading.webp", "🥺", "Mata Melas"),
        AnimatedEmojiItem("screaming.webp", "😱", "Histeris"),
        AnimatedEmojiItem("hot.webp", "🥵", "Kepanasan"),
        AnimatedEmojiItem("cold.webp", "🥶", "Kedinginan"),
        AnimatedEmojiItem("zany.webp", "🤪", "Gokil"),
        AnimatedEmojiItem("wink.webp", "😉", "Kedip"),
        AnimatedEmojiItem("drooling.webp", "🤤", "Ngiler"),
        AnimatedEmojiItem("sleeping.webp", "😴", "Tidur"),
        AnimatedEmojiItem("yawn.webp", "🥱", "Menguap"),
        AnimatedEmojiItem("smirk.webp", "😏", "Senyum Nakal"),
        AnimatedEmojiItem("melting.webp", "🫠", "Meleleh"),
        AnimatedEmojiItem("salute.webp", "🫡", "Hormat"),
        AnimatedEmojiItem("shush.webp", "🤫", "Rahasia"),
        AnimatedEmojiItem("vomit.webp", "🤮", "Mual Muntah"),
        AnimatedEmojiItem("angry.webp", "😡", "Marah"),
        AnimatedEmojiItem("skull.webp", "💀", "Tengkorak"),
        AnimatedEmojiItem("poo.webp", "💩", "Kotoran"),
        AnimatedEmojiItem("clown.webp", "🤡", "Badut"),
        AnimatedEmojiItem("ghost.webp", "👻", "Hantu"),
        AnimatedEmojiItem("party_popper.webp", "🎉", "Konfeti")
    )
}
