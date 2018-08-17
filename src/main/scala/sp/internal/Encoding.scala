package sp.internal

import java.util

/**
  * Taken from Play internals
  */
object Encoding {

  private def charSeqToBitSet(chars: Seq[Char]): util.BitSet = {
    val ints: Seq[Int] = chars.map(_.toInt)
    val max = ints.fold(0)(Math.max)
    assert(max <= 256) // We should only be dealing with 7 or 8 bit chars
    val bitSet = new util.BitSet(max)
    ints.foreach(bitSet.set)
    bitSet
  }

  private val AlphaNum: Seq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val AttrCharPunctuation: Seq[Char] = Seq('!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~')
  private val AttrChar: util.BitSet = charSeqToBitSet(AlphaNum ++ AttrCharPunctuation)
  private val Separators: Seq[Char] = Seq('(', ')', '<', '>', '@', ',', ';', ':', '/', '[', ']', '?', '=', '{', '}', ' ')
  private val PlaceholderChar: Char = '?'

  private val PartialQuotedText: util.BitSet = charSeqToBitSet(
    AlphaNum ++ AttrCharPunctuation ++
      // we include 'separators' plus some chars excluded from 'attr-char'
      Separators ++ Seq('*', '\''))


  def encodeToBuilder(name: String, value: String, builder: StringBuilder): Unit = {

    // This flag gets set if we encounter extended characters when rendering the
    // regular parameter value.
    var hasExtendedChars = false

    // Render ASCII parameter
    // E.g. naïve.txt --> "filename=na_ve.txt"

    builder.append(name)
    builder.append("=\"")

    // Iterate over code points here, because we only want one
    // ASCII character or placeholder per logical character. If
    // we use the value's encoded bytes or chars then we might
    // end up with multiple placeholders per logical character.
    value.codePoints().forEach((codePoint: Int) => {
      // We could support a wider range of characters here by using
      // the 'token' or 'quoted printable' encoding, however it's
      // simpler to use the subset of characters that is also valid
      // for extended attributes.
      if (codePoint >= 0 && codePoint <= 255 && PartialQuotedText.get(codePoint)) {
        builder.append(codePoint.toChar)
      } else {
        // Set flag because we need to render an extended parameter.
        hasExtendedChars = true
        // Render a placeholder instead of the unsupported character.
        builder.append(PlaceholderChar)
      }
    })

    builder.append('"')

    // Optionally render extended, UTF-8 encoded parameter
    // E.g. naïve.txt --> "; filename*=utf8''na%C3%AFve.txt"
    //
    // Renders both regular and extended parameters, as suggested by:
    // - https://tools.ietf.org/html/rfc5987#section-4.2
    // - https://tools.ietf.org/html/rfc6266#section-4.3 (for Content-Disposition filename parameter)

    if (hasExtendedChars) {

      def hexDigit(x: Int): Char = (if (x < 10) x + '0' else x - 10 + 'a').toChar

      // From https://tools.ietf.org/html/rfc5987#section-3.2.1:
      //
      // Producers MUST use either the "UTF-8" ([RFC3629]) or the "ISO-8859-1"
      // ([ISO-8859-1]) character set.  Extension character sets (mime-

      val CharacterSetName = "utf-8"

      builder.append("; ")
      builder.append(name)

      builder.append("*=")
      builder.append(CharacterSetName)
      builder.append("''")

      // From https://tools.ietf.org/html/rfc5987#section-3.2.1:
      //
      // Inside the value part, characters not contained in attr-char are
      // encoded into an octet sequence using the specified character set.
      // That octet sequence is then percent-encoded as specified in Section
      // 2.1 of [RFC3986].
      val bytes = value.getBytes(CharacterSetName)
      for (b <- bytes) {
        if (AttrChar.get(b & 0xFF)) {
          builder.append(b.toChar)
        } else {
          builder.append('%')
          builder.append(hexDigit((b >> 4) & 0xF))
          builder.append(hexDigit(b & 0xF))
        }
      }
    }
  }
}