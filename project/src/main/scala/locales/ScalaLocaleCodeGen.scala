package locales

import java.io.{File, FileInputStream, InputStreamReader}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Files
import java.util.function.IntPredicate
import javax.xml.parsers.SAXParserFactory

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.xml.{XML, _}

/**
  * Value objects build out of CLDR XML data
  */
case class Calendar(id: String) {
  val scalaSafeName: String = id.replace("-", "_")
}

case class MonthSymbols(months: Seq[String], shortMonths: Seq[String])
object MonthSymbols {
  val zero = MonthSymbols(Seq.empty, Seq.empty)
}

case class WeekdaysSymbols(weekdays: Seq[String], shortWeekdays: Seq[String])
object WeekdaysSymbols {
  val zero = WeekdaysSymbols(Seq.empty, Seq.empty)
}

case class AmPmSymbols(amPm: Seq[String])
object AmPmSymbols {
  val zero = AmPmSymbols(Seq.empty)
}

case class EraSymbols(eras: Seq[String])
object EraSymbols {
  val zero = EraSymbols(Seq.empty)
}

case class CalendarSymbols(months: MonthSymbols, weekdays: WeekdaysSymbols,
    amPm: AmPmSymbols, eras: EraSymbols)

case class DateTimePattern(patternType: String, pattern: String)

case class CalendarPatterns(datePatterns: List[DateTimePattern], timePatterns: List[DateTimePattern])

object CalendarPatterns {
  val zero = CalendarPatterns(Nil, Nil)
}

case class NumericSystem(id: String, digits: String)

case class NumberSymbols(system: NumericSystem,
    aliasOf: Option[NumericSystem] = None,
    decimal: Option[Char] = None,
    group: Option[Char] = None,
    list: Option[Char] = None,
    percent: Option[Char] = None,
    plus: Option[Char] = None,
    minus: Option[Char] = None,
    perMille: Option[Char] = None,
    infinity: Option[String] = None,
    nan: Option[String] = None,
    exp: Option[String] = None)

object NumberSymbols {
  def alias(system: NumericSystem, aliasOf: NumericSystem): NumberSymbols =
    NumberSymbols(system, aliasOf = Some(aliasOf))
}

case class XMLLDMLLocale(language: String, territory: Option[String],
    variant: Option[String], script: Option[String])

case class XMLLDML(locale: XMLLDMLLocale, fileName: String, defaultNS: Option[NumericSystem],
    digitSymbols: Map[NumericSystem, NumberSymbols], calendar: Option[CalendarSymbols],
    datePatterns: Option[CalendarPatterns]) {

  val scalaSafeName: String = {
    List(Some(locale.language), locale.script, locale.territory, locale.variant)
      .flatten.mkString("_")
  }
}

object CodeGenerator {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._

  val autoGeneratedCommend = "Auto-generated code from CLDR definitions, don't edit"
  val autoGeneratedISO639_2 = "Auto-generated code from ISO 639-2 data, don't edit"

  def buildClassTree(packageObject: String, ldmls: List[XMLLDML],
      only: List[String], parentLocales: Map[String, List[String]]): Tree = {
    val langs = ldmls.map(_.scalaSafeName.split("_").toList)
    // Root must always be available
    val root = ldmls.find(_.scalaSafeName == "root").get

    val objectBlock = if (only.nonEmpty) {
       ldmls.filter(a => only.contains(a.scalaSafeName))
        .map(buildClassTree(root, langs, parentLocales))
      } else {
        ldmls.map(buildClassTree(root, langs, parentLocales))
      }

    BLOCK (
      List(IMPORT("locales.cldr.LDML") withComment autoGeneratedCommend,
      IMPORT("locales.cldr.LDMLLocale"),
      IMPORT("locales.cldr.Symbols"),
      IMPORT("locales.cldr.CalendarSymbols"),
      IMPORT("locales.cldr.CalendarPatterns"),
      IMPORT("locales.cldr.data.numericsystems._")) ++ objectBlock
    ) inPackage "locales.cldr.data"
  }

  def findParent(root: XMLLDML, langs: List[List[String]],
      ldml: XMLLDML, parentLocales: Map[String, List[String]]): Option[String] = {
    // http://www.unicode.org/reports/tr35/#Locale_Inheritance
    parentLocales.find(_._2.contains(ldml.fileName)).fold(
      // This searches based on the simple hierarchy resolution based on bundle_name
      // http://www.unicode.org/reports/tr35/#Bundle_vs_Item_Lookup
      ldml.scalaSafeName.split("_").reverse.toList match {
        case x :: Nil if x == root.scalaSafeName => None
        case x :: Nil => Some(root.scalaSafeName)
        case x :: xs if langs.contains(xs.reverse) => Some(xs.reverse.mkString("_"))
      }
    )(p => Some(p._1))
  }

  def buildClassTree(root: XMLLDML, langs: List[List[String]], parentLocales: Map[String, List[String]])
      (ldml: XMLLDML): Tree = {
    val ldmlSym = getModule("LDML")
    val ldmlNumericSym = getModule("Symbols")
    val ldmlCalendarSym = getModule("CalendarSymbols")
    val ldmlCalendarPatternsSym = getModule("CalendarPatterns")
    val ldmlLocaleSym = getModule("LDMLLocale")

    val parent = findParent(root, langs, ldml, parentLocales).fold(NONE)(v => SOME(REF(v)))

    val ldmlLocaleTree = Apply(ldmlLocaleSym, LIT(ldml.locale.language),
      ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))),
      ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))),
      ldml.locale.script.fold(NONE)(s => SOME(LIT(s))))

    val defaultNS = ldml.defaultNS.fold(NONE)(s => SOME(REF(s.id)))

    // Locales only use the default numeric system
    val numericSymbols = ldml.digitSymbols.map { case (ns, symb) =>
      val decimal = symb.decimal.fold(NONE)(s => SOME(LIT(s)))
      val group = symb.group.fold(NONE)(s => SOME(LIT(s)))
      val list = symb.list.fold(NONE)(s => SOME(LIT(s)))
      val percent = symb.percent.fold(NONE)(s => SOME(LIT(s)))
      val minus = symb.minus.fold(NONE)(s => SOME(LIT(s)))
      val perMille = symb.perMille.fold(NONE)(s => SOME(LIT(s)))
      val infinity = symb.infinity.fold(NONE)(s => SOME(LIT(s)))
      val nan = symb.nan.fold(NONE)(s => SOME(LIT(s)))
      val exp = symb.exp.fold(NONE)(s => SOME(LIT(s)))
      Apply(ldmlNumericSym, REF(ns.id),
        symb.aliasOf.fold(NONE)(n => SOME(REF(n.id))), decimal, group, list,
        percent, minus, perMille, infinity, nan, exp)
    }

    val gc = ldml.calendar.map { cs =>
      Apply(ldmlCalendarSym, LIST(cs.months.months.map(LIT(_))), LIST(cs.months.shortMonths.map(LIT(_))),
        LIST(cs.weekdays.weekdays.map(LIT(_))), LIST(cs.weekdays.shortWeekdays.map(LIT(_))),
        LIST(cs.amPm.amPm.map(LIT(_))), LIST(cs.eras.eras.map(LIT(_))))
    }.fold(NONE)(s => SOME(s))

    val gcp = ldml.datePatterns.map { cs =>
      def patternToIndex(i: String) = i match {
        case "full" => 0
        case "long" => 1
        case "medium" => 2
        case "short" => 3
        case x => throw new IllegalArgumentException(s"Unknown format $x, abort ")
      }

      val dates = MAKE_MAP(cs.datePatterns.map(p => TUPLE(LIT(patternToIndex(p.patternType)), LIT(p.pattern))))
      val times = MAKE_MAP(cs.timePatterns.map(p => TUPLE(LIT(patternToIndex(p.patternType)), LIT(p.pattern))))
      Apply(ldmlCalendarPatternsSym, dates, times)
    }.fold(NONE)(s => SOME(s))

    OBJECTDEF(ldml.scalaSafeName) withParents Apply(ldmlSym, parent,
      ldmlLocaleTree, defaultNS, LIST(numericSymbols), gc, gcp)
  }

  def metadata(codes: List[String],
               languages: List[String],
               scripts: List[String],
               territoryCodes: Map[String, String],
               iso3Languages: Map[String, String]): Tree = {
    BLOCK (
      OBJECTDEF("metadata") := BLOCK(
        LAZYVAL("isoCountries", "Array[String]") :=
          ARRAY(codes.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("iso3Countries", "Map[String, String]") :=
          MAKE_MAP(territoryCodes.map{case (k, v) => LIT(k) ANY_-> LIT(v)}) withComment autoGeneratedCommend,
        LAZYVAL("isoLanguages", "Array[String]") :=
          ARRAY(languages.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("iso3Languages", "Map[String, String]") :=
          MAKE_MAP(iso3Languages.map{case (k, v) => LIT(k) ANY_-> LIT(v)}) withComment autoGeneratedISO639_2,
        LAZYVAL("scripts", "Array[String]") :=
          ARRAY(scripts.map(LIT(_))) withComment autoGeneratedCommend
      )
    ) inPackage "locales.cldr.data"
  }

  def numericSystems(ns: Seq[NumericSystem]): Tree = {
    val ldmlNS = getModule("NumberingSystem")

    BLOCK (
      IMPORT("locales.cldr.NumberingSystem") withComment autoGeneratedCommend,
      OBJECTDEF("numericsystems") := BLOCK(
        ns.map(s =>
          LAZYVAL(s.id, "NumberingSystem") :=
            Apply(ldmlNS, LIT(s.id), LIST(s.digits.toList.map(LIT(_))))
        )
      )
    ) inPackage "locales.cldr.data"
  }

  def calendars(c: Seq[Calendar]): Tree = {
    val ldmlNS = getModule("Calendar")

    BLOCK (
      IMPORT("locales.cldr.Calendar") withComment autoGeneratedCommend,
      OBJECTDEF("calendars") := BLOCK(
        (LAZYVAL("all", "List[Calendar]") := LIST(c.map(c => REF(c.scalaSafeName)))) +:
        c.map(c =>
          LAZYVAL(c.scalaSafeName, "Calendar") :=
            Apply(ldmlNS, LIT(c.id))
        )
      )
    ) inPackage "scala.scalajs.locale.cldr.data"
  }
}

object ScalaLocaleCodeGen {
  def writeGeneratedTree(base: File, file: String, tree: treehugger.forest.Tree):File = {
    val dataPath = base.toPath.resolve("locales").resolve("cldr").resolve("data")
    val path = dataPath.resolve(s"$file.scala")

    path.getParent.toFile.mkdirs()
    println(s"Write to $path")

    Files.write(path, treehugger.forest.treeToString(tree)
      .getBytes(Charset.forName("UTF8")))
    path.toFile
  }

  val unicodeIgnorable = new IntPredicate {
    override def test(value: Int): Boolean = !Character.isIdentifierIgnorable(value)
  }

  def readCalendarData(xml: Node): Option[CalendarSymbols] = {
    def readEntries(mc: Node, itemParent: String, entryName: String, width: String): Seq[String] =
      for {
        w <- mc \ itemParent
        if (w \ "@type").text == width
        m <- w \\ entryName
      } yield m.text

    // read the months context
    val months = (for {
      mc <- xml \\ "monthContext"
      if (mc \ "@type").text == "format"
    } yield {
      val wideMonths = readEntries(mc, "monthWidth", "month", "wide")
      val shortMonths = readEntries(mc, "monthWidth", "month", "abbreviated")
      MonthSymbols(wideMonths, shortMonths)
    }).headOption

    // read the weekdays context
    val weekdays = (for {
      mc <- xml \\ "dayContext"
      if (mc \ "@type").text == "format"
    } yield {
      val weekdays = readEntries(mc, "dayWidth", "day", "wide")
      val shortWeekdays = readEntries(mc, "dayWidth", "day", "abbreviated")
      WeekdaysSymbols(weekdays, shortWeekdays)
    }).headOption

    def readPeriod(n: Node, name: String): Option[String] =
      (for {
        p <- n \ "dayPeriod"
        if (p \ "@type").text == name && (p \ "@alt").text != "variant"
      } yield p.text).headOption

    // read the day periods
    val amPm = (for {
        dpc <- xml \\ "dayPeriods" \ "dayPeriodContext"
        if (dpc \ "@type").text == "format"
        dpw <- dpc \\ "dayPeriodWidth"
        if (dpw \ "@type").text == "wide"
      } yield {
        val am = readPeriod(dpw, "am")
        val pm = readPeriod(dpw, "pm")
        // This is valid because by the spec am and pm must appear together
        // http://www.unicode.org/reports/tr35/tr35-dates.html#Day_Period_Rules
        AmPmSymbols(List(am, pm).flatten)
      }).headOption

    def readEras(n: Node, idx: String): Option[String] =  {
      (for {
        p <- n \ "eraAbbr" \\ "era"
        if (p \ "@type").text == idx && (p \ "@alt").text != "variant"
      } yield p.text).headOption
    }

    val eras = (for {
          n <- xml \ "eras"
        } yield {
          val bc = readEras(n, "0")
          val ad = readEras(n, "1")
          EraSymbols(List(bc, ad).flatten)
        }).headOption

    if (List(months, weekdays, amPm, eras).exists(_.isDefined)) {
      Some(CalendarSymbols(months.getOrElse(MonthSymbols.zero),
        weekdays.getOrElse(WeekdaysSymbols.zero), amPm.getOrElse(AmPmSymbols.zero),
        eras.getOrElse(EraSymbols.zero)))
    } else {
      None
    }
  }

  def readCalendarPatterns(xml: Node): Option[CalendarPatterns] = {
    def readPatterns(n: Node, sub: String, formatType: String): Seq[DateTimePattern] =
      for {
        ft <- n \ formatType
        p <- ft \ sub \ "pattern"
        if (p \ "@alt").text != "variant"
      } yield DateTimePattern((ft \ "@type").text, p.text)

    val datePatterns = (for {
        df <- xml \\ "dateFormats"
      } yield {
        readPatterns(df, "dateFormat", "dateFormatLength")
      }).headOption.map(_.toList)

    val timePatterns = (for {
        df <- xml \\ "timeFormats"
      } yield {
        readPatterns(df, "timeFormat", "timeFormatLength")
      }).headOption.map(_.toList)

    Some(CalendarPatterns(datePatterns.getOrElse(Nil), timePatterns.getOrElse(Nil)))
  }

  def parseTerritoryCodes(xml: Node): Map[String, String] = {
    (for {
      territoryCodes <- xml \ "codeMappings" \ "territoryCodes"
      alpha2          = (territoryCodes \ "@type").text
      alpha3          = Option((territoryCodes \ "@alpha3").text).filter(_.nonEmpty)
      entry           = alpha3.map(alpha2 -> _)
    } yield entry).flatten.toMap
  }

  /**
    * Parse the xml into an XMLLDML object
    */
  def constructLDMLDescriptor(f: File, xml: Elem, latn: NumericSystem,
      ns: Map[String, NumericSystem]): XMLLDML = {
    // Parse locale components
    val language = (xml \ "identity" \ "language" \ "@type").text
    val territory = Option((xml \ "identity" \ "territory" \ "@type").text)
      .filter(_.nonEmpty)
    val variant = Option((xml \ "identity" \ "variant" \ "@type").text)
      .filter(_.nonEmpty)
    val script = Option((xml \ "identity" \ "script" \ "@type").text)
      .filter(_.nonEmpty)

    val gregorian = for {
        n <- xml \ "dates" \\ "calendar"
        if (n \ "@type").text == "gregorian"
        if n.text.nonEmpty
      } yield readCalendarData(n)

    val gregorianDatePatterns = for {
        n <- xml \ "dates" \\ "calendar"
        if (n \ "@type").text == "gregorian"
        if n.text.nonEmpty
      } yield readCalendarPatterns(n)

    // Find out the default numeric system
    val defaultNS = Option((xml \ "numbers" \ "defaultNumberingSystem").text)
      .filter(_.nonEmpty).filter(ns.contains)
    def symbolS(n: NodeSeq): Option[String] = if (n.isEmpty) None else Some(n.text)

    def symbolC(n: NodeSeq): Option[Char] = if (n.isEmpty) None else {
      // Filter out the ignorable code points, e.g. RTL and LTR marks
      val charInt = n.text.codePoints().filter(unicodeIgnorable).findFirst().orElse(0)
      Some(charInt.toChar)
    }

    val symbols = (xml \ "numbers" \\ "symbols").flatMap { s =>
      // http://www.unicode.org/reports/tr35/tr35-numbers.html#Numbering_Systems
      // By default, number symbols without a specific numberSystem attribute
      // are assumed to be used for the "latn" numbering system, which i
      // western (ASCII) digits
      val nsAttr = Option((s \ "@numberSystem").text).filter(_.nonEmpty)
      val sns = nsAttr.flatMap(ns.get).getOrElse(latn)
      // TODO process aliases
      val nsSymbols = s.collect {
        case s @ <symbols>{_*}</symbols> if (s \ "alias").isEmpty =>
          // elements may not be present and they could be the empty string
          val decimal = symbolC(s \ "decimal")
          val group = symbolC(s \ "group")
          val list = symbolC(s \ "list")
          val percentSymbol = symbolC(s \ "percentSign")
          val plusSign = symbolC(s \ "plusSign")
          val minusSign = symbolC(s \ "minusSign")
          val perMilleSign = symbolC(s \ "perMille")
          val infiniteSign = symbolS(s \ "infinity")
          val nan = symbolS(s \ "nan")
          val exp = symbolS(s \ "exponential")
          val sym = NumberSymbols(sns, None, decimal, group, list,
            percentSymbol, plusSign, minusSign, perMilleSign, infiniteSign, nan, exp)
          sns -> sym

        case s @ <symbols>{_*}</symbols> =>
          // We take advantage that all aliases on CLDR are to latn
          sns -> NumberSymbols.alias(sns, latn)
      }
      nsSymbols
    }

    val fileName = f.getName.substring(0, f.getName.lastIndexOf("."))

    XMLLDML(XMLLDMLLocale(language, territory, variant, script), fileName,
      defaultNS.flatMap(ns.get), symbols.toMap, gregorian.flatten.headOption, gregorianDatePatterns.flatten.headOption)
  }

  // Note this must be a def or there could be issues with concurrency
  def parser: SAXParser = {
    val f = SAXParserFactory.newInstance()
    // Don't validate to speed up generation
    f.setNamespaceAware(false)
    f.setValidating(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  def parseNumberingSystems(xml: Elem): Seq[NumericSystem] = {
    val ns = xml \ "numberingSystems" \\ "numberingSystem"

    for {
      n <- ns
      if (n \ "@type").text == "numeric" // ignore algorithmic ns
    } yield {
      val id = (n \ "@id").text
      val digits = (n \ "@digits").text
      NumericSystem(id, digits)
    }
  }

  def readNumericSystems(data: File): Seq[NumericSystem] = {
    // Parse the numeric systems
    val numberingSystemsFile = data.toPath.resolve("common")
      .resolve("supplemental").resolve("numberingSystems.xml").toFile
    parseNumberingSystems(XML.withSAXParser(parser).loadFile(numberingSystemsFile))
  }

  def generateNumericSystemsFile(base: File, numericSystems: Seq[NumericSystem]): File = {
    // Generate numeric systems source code
    writeGeneratedTree(base, "numericsystems",
      CodeGenerator.numericSystems(numericSystems))
  }

  def parseCalendars(xml: Elem): Seq[Calendar] = {
    val ns = xml \ "calendarData" \\ "calendar"

    for {
      n <- ns
    } yield {
      val id = (n \ "@type").text
      Calendar(id)
    }
  }

  def readCalendars(data: File): Seq[Calendar] = {
    // Parse the numeric systems
    val calendarsSupplementalData = data.toPath.resolve("common")
      .resolve("supplemental").resolve("supplementalData.xml").toFile
    parseCalendars(XML.withSAXParser(parser).loadFile(calendarsSupplementalData))
  }

  def readTerritoryCodes(data: File): Map[String, String] = {
    val territorySupplementalData = data.toPath.resolve("common")
      .resolve("supplemental").resolve("supplementalData.xml").toFile
    parseTerritoryCodes(XML.withSAXParser(parser).loadFile(territorySupplementalData))
  }

  def readIso3LanguageCodes(data: File): Map[String, String] = {
    Files.readAllLines(data.toPath, StandardCharsets.UTF_8)
      .asScala
      .flatMap { line =>
        line.split('|') match {
          case Array(bib, ter, alpha2, _, _) if alpha2.nonEmpty =>
            if (ter.nonEmpty) Some(alpha2 -> ter) else Some(alpha2 -> bib)
          case _ => None
        }
      }
      .toMap
  }

  def parseParentLocales(xml: Elem): Map[String, List[String]] = {
    val ns = xml \ "parentLocales" \\ "parentLocale"

    val p = for {
      n <- ns
    } yield {
      val parent = (n \ "@parent").text
      val locales = (n \ "@locales").text
      parent -> locales.split("\\s").toList
    }
    p.toMap
  }

  def readParentLocales(data: File): Map[String, List[String]] = {
    // Parse the parent locales
    val parentLocalesSupplementalData = data.toPath.resolve("common")
      .resolve("supplemental").resolve("supplementalData.xml").toFile
    parseParentLocales(XML.withSAXParser(parser).loadFile(parentLocalesSupplementalData))
  }

  def generateCalendarsFile(base: File, calendars: Seq[Calendar]): File = {
    // Generate numeric systems source code
    writeGeneratedTree(base, "calendars",
      CodeGenerator.calendars(calendars))
  }

  def buildLDMLDescriptors(data: File, numericSystemsMap: Map[String, NumericSystem],
      latnNS: NumericSystem): List[XMLLDML] = {
    // All files under common/main
    val files = Files.newDirectoryStream(data.toPath.resolve("common")
      .resolve("main")).iterator().asScala.toList

    for {
      f <- files.map(k => k.toFile)
      //if f.getName == "en.xml" || f.getName == "root.xml"
      r = new InputStreamReader(new FileInputStream(f), "UTF-8")
    } yield constructLDMLDescriptor(f, XML.withSAXParser(parser).load(r),
      latnNS, numericSystemsMap)
  }

  def generateLocalesFile(base: File, clazzes: List[XMLLDML], parentLocales: Map[String, List[String]]): File = {
    val names = clazzes.map(_.scalaSafeName)

    // Generate locales code
    val stdTree = CodeGenerator.buildClassTree("data", clazzes, names, parentLocales)
    writeGeneratedTree(base, "data", stdTree)
  }

  def generateMetadataFile(base: File,
                           clazzes: List[XMLLDML],
                           territoryCodes: Map[String, String],
                           iso3LanguageCodes: Map[String, String]): File = {
    val isoCountryCodes = clazzes.flatMap(_.locale.territory).distinct
      .filter(_.length == 2).sorted
    val isoLanguages = clazzes.map(_.locale.language).distinct
      .filter(_.length == 2).sorted
    val scripts = clazzes.flatMap(_.locale.script).distinct.sorted
    // Generate metadata source code
    writeGeneratedTree(base, "metadata",
      CodeGenerator.metadata(isoCountryCodes, isoLanguages, scripts, territoryCodes, iso3LanguageCodes))
  }

  def generateDataSourceCode(base: File, data: File): Seq[File] = {
    val nanos = System.nanoTime()
    val numericSystems = readNumericSystems(data)
    val f1 = generateNumericSystemsFile(base, numericSystems)

    val calendars = readCalendars(data)
    val parentLocales = readParentLocales(data)
    val f2 = generateCalendarsFile(base, calendars)

    val numericSystemsMap: Map[String, NumericSystem] =
      numericSystems.map(n => n.id -> n)(breakOut)
    // latn NS must exist, break if not found
    val latnNS = numericSystemsMap("latn")

    val territoryCodes = readTerritoryCodes(data)
    val iso3LanguageCodes = readIso3LanguageCodes(new File("project/src/main/resources/ISO-639-2_utf-8.2019-05-29.txt"))

    val ldmls = buildLDMLDescriptors(data, numericSystemsMap, latnNS)

    val f3 = generateMetadataFile(base, ldmls, territoryCodes, iso3LanguageCodes)
    val f4 = generateLocalesFile(base, ldmls, parentLocales)

    println("Generation took " + (System.nanoTime() - nanos) / 1000000 + " [ms]")
    Seq(f1, f2, f3, f4)
  }
}
