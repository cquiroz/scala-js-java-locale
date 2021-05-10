package locales

import java.util.Locale

object DefaultLocale {
  def platformLocale: Locale = Locale.getDefault()
}
