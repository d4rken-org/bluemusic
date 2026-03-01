package eu.darken.bluemusic.main.ui.settings.support.contact

import eu.darken.bluemusic.R

enum class ContactCategory(
    val labelRes: Int,
    val subjectTag: String,
    val hintRes: Int,
) {
    QUESTION(R.string.contact_category_question_label, "QUESTION", R.string.contact_description_question_hint),
    FEATURE(R.string.contact_category_feature_label, "FEATURE", R.string.contact_description_feature_hint),
    BUG(R.string.contact_category_bug_label, "BUG", R.string.contact_description_bug_hint),
}
