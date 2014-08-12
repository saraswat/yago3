package fromWikipedia;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javatools.administrative.Announce;
import javatools.administrative.D;
import javatools.datatypes.FinalSet;
import javatools.filehandlers.FileLines;
import javatools.parsers.Char17;
import javatools.parsers.Name;
import javatools.parsers.NounGroup;
import javatools.parsers.PlingStemmer;
import javatools.util.FileUtils;
import utils.FactCollection;
import utils.PatternList;
import utils.TermParser;
import utils.Theme;
import utils.TitleExtractor;
import basics.Fact;
import basics.FactComponent;
import basics.RDFS;
import basics.YAGO;
import extractors.EnglishWikipediaExtractor;
import followUp.FollowUpExtractor;
import followUp.Redirector;
import followUp.TypeChecker;
import fromOtherSources.HardExtractor;
import fromOtherSources.PatternHardExtractor;
import fromOtherSources.WordnetExtractor;
import fromThemes.CategoryClassExtractor;

/**
 * TemporalInfoboxExtractor - YAGO2s
 * 
 * Extract temporal facts from infoboxes. It uses the patterns
 * /data/_infoboxTemporalPatterns.ttl for the extraction.
 * 
 * @author Erdal Kuzey
 * 
 */
public class TemporalInfoboxExtractor extends EnglishWikipediaExtractor {

	public Set<FollowUpExtractor> followUp() {
		return new FinalSet<FollowUpExtractor>(
				new Redirector(TEMPORALDIRTYINFOBOXFACTS,
						TEMPORALREDIRECTEDINFOBOXFACTS, this), new TypeChecker(
						TEMPORALREDIRECTEDINFOBOXFACTS, TEMPORALINFOBOXFACTS,
						this));
	}

	public Set<Theme> input() {
		return new HashSet<Theme>(Arrays.asList(
				PatternHardExtractor.CATEGORYPATTERNS,
				WordnetExtractor.PREFMEANINGS,
				PatternHardExtractor.INFOBOXTEMPORALPATTERNS,
				WordnetExtractor.WORDNETWORDS,
				PatternHardExtractor.TITLEPATTERNS,
				HardExtractor.HARDWIREDFACTS));
	}

	/** Infobox facts, non-checked */
	public static final Theme TEMPORALDIRTYINFOBOXFACTS = new Theme(
			"infoboxTemporalFactsVeryDirty",
			"Temporal facts extracted from the Wikipedia infoboxes - still to be redirect-checked and type-checked");
	/** Redirected Infobox facts, non-checked */
	public static final Theme TEMPORALREDIRECTEDINFOBOXFACTS = new Theme(
			"infoboxTemporalFactsDirty",
			"Temporal facts extracted from the Wikipedia infoboxes with redirects resolved - still to be type-checked");
	/** Final Infobox facts */
	public static final Theme TEMPORALINFOBOXFACTS = new Theme(
			"infoboxTemporalFacts",
			"Temporal facts extracted from the Wikipedia infoboxes, type-checked and with redirects resolved");
	/** Infobox sources */
	public static final Theme TEMPORALINFOBOXSOURCES = new Theme(
			"infoboxTemporalSources",
			"Source information for the temporal facts extracted from the Wikipedia infoboxes");
	/** Types derived from infoboxes */
	public static final Theme INFOBOXTYPES = new Theme("infoboxTemporalTypes",
			"Types extracted from Wikipedia infoboxes");

	public Set<Theme> output() {
		return new FinalSet<Theme>(TEMPORALDIRTYINFOBOXFACTS, INFOBOXTYPES,
				TEMPORALINFOBOXSOURCES);
	}
	/** Holds the nonconceptual categories. Needed for political role extraction. */
	protected Set<String> nonConceptualCategories;

	/** Holds the preferred meanings. Needed for political role extraction. */
	protected Map<String, String> preferredMeanings;
	@Override
	public Set<Theme> inputCached() {
		return new FinalSet<>(HardExtractor.HARDWIREDFACTS);
	}

	public void extract() throws Exception {


		
		FactCollection infoboxFacts = PatternHardExtractor.INFOBOXTEMPORALPATTERNS
				.factCollection();
		FactCollection hardWiredFacts = HardExtractor.HARDWIREDFACTS
				.factCollection();
		Map<String, Set<String>> patterns = TemporalInfoboxExtractor
				.infoboxPatterns(infoboxFacts);
		PatternList replacements = new PatternList(infoboxFacts,
				"<_infoboxReplace>");
		Map<String, String> combinations = infoboxFacts
				.collectSubjectAndObjectAsStrings("<_infoboxCombine>");
		Map<String, String> preferredMeaning = WordnetExtractor.PREFMEANINGS
				.factCollection().getPreferredMeanings();
		TitleExtractor titleExtractor = new TitleExtractor("en");

		// Extract the information
		// Announce.progressStart("Extracting", 4_500_000);
		Reader in = FileUtils.getBufferedUTF8Reader(inputData);
		String titleEntity = null;
		while (true) {
			switch (FileLines.findIgnoreCase(in, "<title>", "{{Infobox",
					"{{ Infobox")) {
			case -1:
				// Announce.progressDone();
				in.close();
				return;
			case 0:
				// Announce.progressStep();
				titleEntity = titleExtractor.getTitleEntity(in);
				break;
			default:
				if (titleEntity == null)
					continue;
				String cls = FileLines.readTo(in, '}', '|').toString().trim();
				String type = preferredMeaning.get(cls);
				if (type != null) {
					write(INFOBOXTYPES, new Fact(null, titleEntity, RDFS.type,
							type), TEMPORALINFOBOXSOURCES,
							FactComponent.wikipediaURL(titleEntity),
							"TemporalInfoboxExtractor: Preferred meaning of infobox type "
									+ cls);
				}
				Map<String, Set<String>> attributes = readInfobox(in,
						combinations);
				for (String attribute : attributes.keySet()) {
					Set<String> relations = patterns.get(attribute);
					if (relations == null)
						continue;
					for (String relation : relations) {
						for (String value : attributes.get(attribute)) {
							extract(titleEntity, value, relation,
									preferredMeaning, hardWiredFacts,
									replacements);
						}
					}
				}
			}
		}
	}

	/** Extracts a relation from a string */
	@SuppressWarnings("static-access")
	protected void extract(String entity, String valueString, String relation,
			Map<String, String> preferredMeanings,
			FactCollection factCollection, PatternList replacements)
			throws IOException {
		

		
		// If the relation is for a combined attribute
		if (relation.contains(",")) {
			extractMetaFact(entity, valueString, relation, preferredMeanings,
					factCollection, replacements);
		} else {
			Fact baseFact = new Fact("", "", "");
			valueString = replacements.transform(Char17
					.decodeAmpersand(valueString));
			valueString = valueString.replace("$0",
					FactComponent.stripBrackets(entity));
			valueString = valueString.trim();
			if (valueString.length() == 0)
				return;

			// Check inverse
			boolean inverse;
			String cls;
			if (relation.endsWith("->")) {
				inverse = true;
				relation = Char17.cutLast(Char17.cutLast(relation)) + '>';
				cls = factCollection.getObject(relation, RDFS.domain);
			} else {
				inverse = false;
				cls = factCollection.getObject(relation, RDFS.range);
			}
			if (cls == null) {
				Announce.warning("Unknown relation to extract:", relation);
				cls = YAGO.entity;
			}

			// Get the term extractor
			TermParser extractor = cls.equals(RDFS.clss) ? new TermParser.ForClass(
					preferredMeanings) : TermParser.forType(cls);
//			String syntaxChecker = FactComponent.asJavaString(factCollection
//					.getObject(cls, "<_hasTypeCheckPattern>"));

			// Extract all terms
			List<String> objects = extractor.extractList(valueString);
			// extract multivalues, such as,
			// spouse=[[[Shawn Andrews(actor)|Shawn Andrews]] (1992-1993)\n[[Luc
			// Besson]] (1997-1999)
			String[] multiValues = valueString.split("\\n");
			ArrayList<List<String>> dateObjectsList = new ArrayList<>(10);
			if (multiValues.length > 1) {
				for (int i = 0; i < multiValues.length; i++) {
					dateObjectsList.add(extractor.forDate
							.extractList(multiValues[i]));

				}
			} else if (valueString.contains("\t")) {
				multiValues = new String[] { valueString };
				for (int i = 0; i < multiValues.length; i++) {
					dateObjectsList.add(extractor.forDate
							.extractList(multiValues[i]
									.substring(multiValues[i].indexOf('\t'))));
				}
			}

			for (int i = 0; i < objects.size(); i++) {
				String object = objects.get(i);
				// Check syntax
//				if (syntaxChecker != null
//						&& !FactComponent.asJavaString(object).matches(
//								syntaxChecker)) {
//					Announce.debug("Extraction", object, "for", entity,
//							relation, "does not match syntax check",
//							syntaxChecker);
//					continue;
//				}
				// Check data type
				if (FactComponent.isLiteral(object)) {
					String datatype = FactComponent.getDatatype(object);
					if (datatype == null)
						datatype = YAGO.string;
					if (cls.equals(YAGO.languageString)
							&& datatype.equals(YAGO.string)) {
						object = FactComponent.setLanguage(object, "eng");
					} else {
						if (!factCollection.isSubClassOf(datatype, cls)) {
							Announce.debug("Extraction", object, "for", entity,
									relation, "does not match typecheck", cls);
							continue;
						}
						object = FactComponent.setDataType(object, cls);
					}
				}
				// check if the relation is <holdsPoliticalPosition>, then map arg2 to particular wordnet position.
				if(relation.equals("<holdsPoliticalPosition>")){
					object= getWordnetClassForPoliticalPosition(object, preferredMeanings);
					if(object==null)
						continue;
				}
				if (inverse) {
					baseFact = new Fact(object, relation, entity);
					if (baseFact.getArg(2).contains("xsd:date")) // type
																	// checking
																	// for dates
						write(TEMPORALDIRTYINFOBOXFACTS, new Fact(object,
								relation, entity), TEMPORALINFOBOXSOURCES,
								FactComponent.wikipediaURL(entity),
								"TemporalInfoboxExtractor: from " + valueString);
				} else {
					baseFact = new Fact(entity, relation, object);
					if (baseFact.getArg(2).contains("xsd:date"))
						write(TEMPORALDIRTYINFOBOXFACTS, baseFact,
								TEMPORALINFOBOXSOURCES,
								FactComponent.wikipediaURL(entity),
								"TemporalInfoboxExtractor: from " + valueString);

				}

				// attaching dates to basefacts
				// if (dateObjectsList.size() < 1 || multiValues.length < 2)
				// return;
				if (dateObjectsList.size() > 0) {
					try {
						List<String> dates = dateObjectsList.get(i);
						if (dates.size() > 0
								&& (FactComponent.isUri(baseFact.getArg(2)) || FactComponent
										.isLiteral(baseFact.getArg(2)))) {
							write(TEMPORALDIRTYINFOBOXFACTS, baseFact,
									TEMPORALINFOBOXSOURCES, entity,
									"TemporalInfoboxExtractor: from "
											+ valueString);
							Fact metafact = baseFact.metaFact("<occursSince>",
									dates.get(0));
							write(TEMPORALDIRTYINFOBOXFACTS, metafact,
									TEMPORALINFOBOXSOURCES, entity,
									"TemporalInfoboxExtractor: from "
											+ valueString);
							if (dates.size() > 1) {
								metafact = baseFact.metaFact("<occursUntil>",
										dates.get(1));
								write(TEMPORALDIRTYINFOBOXFACTS, metafact,
										TEMPORALINFOBOXSOURCES, entity,
										"TemporalInfoboxExtractor: from "
												+ valueString);
							}

						}
					} catch (Exception e) {
						continue;
					}

				}
				if (factCollection.contains(relation, RDFS.type, YAGO.function))
					break;
			}
		}

	}

	private String getWordnetClassForPoliticalPosition(String object, Map<String, String> preferredMeanings) {
		// TODO Auto-generated method stub
		return category2class(object,preferredMeanings,false);
	}
	public String category2class(String categoryName, Map<String, String> preferredMeanings, boolean pluralityIsImportant) {
		categoryName = FactComponent.stripCat(categoryName);
		// Check out whether the new category is worth being added
		NounGroup category = new NounGroup(categoryName);
		if (category.head() == null) {
			Announce.debug("Could not find type in", categoryName,
					"(has empty head)");
			return (null);
		}

		// If the category is an acronym, drop it
		if (Name.isAbbreviation(category.head())) {
			Announce.debug("Could not find type in", categoryName,
					"(is abbreviation)");
			return (null);
		}
		category = new NounGroup(categoryName.toLowerCase());

		// Only plural words are good hypernyms
		if(pluralityIsImportant){
			if (PlingStemmer.isSingular(category.head())
					&& !category.head().equals("people")) {
				Announce.debug("Could not find type in", categoryName,
						"(is singular)");
				return (null);
			}
		}
		String stemmedHead = PlingStemmer.stem(category.head());

		// Try all premodifiers (reducing the length in each step) + head
		if (category.preModifier() != null) {
			String wordnet = null;
			String preModifier = category.preModifier().replace('_', ' ');

			for (int start = 0; start != -1 && start < preModifier.length() - 2; start = preModifier
					.indexOf(' ', start + 1)) {
				wordnet = preferredMeanings
						.get((start == 0 ? preModifier : preModifier
								.substring(start + 1)) + " " + stemmedHead);
				// take the longest matching sequence
				if (wordnet != null)
					return (wordnet);
			}
		}

		// Try postmodifiers to catch "head of state"
		if (category.postModifier() != null && category.preposition() != null
				&& category.preposition().equals("of")) {
			String wordnet = preferredMeanings.get(stemmedHead + " of "
					+ category.postModifier().head());
			if (wordnet != null)
				return (wordnet);
		}

		// Try head
		String wordnet = preferredMeanings.get(stemmedHead);
		if (wordnet != null)
			return (wordnet);
		Announce.debug("Could not find type in", categoryName, "("
				+ stemmedHead + ") (no wordnet match)");
		return (null);
	}
	/** Extracts a base fact and a metafact by using combined attributes */

	private void extractMetaFact(String entity, String valueString,
			String relation, Map<String, String> preferredMeanings,
			FactCollection factCollection, PatternList replacements)
			throws IOException {
		Fact baseFact = new Fact("", "", "");
		valueString = valueString.replaceAll("\\t\\t", "\tNULL\t");
		String[] relations = relation.split(",");
		String values[] = valueString.split("\\t");

		for (int i = 0; i < relations.length; i++) {
			if (i > values.length - 1)
				return;
			relation = "<" + relations[i].replaceAll("<|>", "") + ">";
			valueString = values[i];
			if (valueString.equals("NULL"))
				continue;
			valueString = replacements.transform(Char17
					.decodeAmpersand(valueString));
			valueString = valueString.replace("$0",
					FactComponent.stripBrackets(entity));
			valueString = valueString.trim();
			if (valueString.length() == 0)
				return;

			// Check inverse
			boolean inverse;
			String cls;
			if (relation.endsWith("->")) {
				inverse = true;
				relation = Char17.cutLast(Char17.cutLast(relation)) + '>';
				cls = factCollection.getObject(relation, RDFS.domain);
			} else {
				inverse = false;
				cls = factCollection.getObject(relation, RDFS.range);
			}
			if (cls == null) {
				Announce.warning("Unknown relation to extract:", relation);
				cls = YAGO.entity;
			}

			// Get the term extractor
			TermParser extractor = cls.equals(RDFS.clss) ? new TermParser.ForClass(
					preferredMeanings) : TermParser.forType(cls);
//			String syntaxChecker = FactComponent.asJavaString(factCollection
//					.getObject(cls, "<_hasTypeCheckPattern>"));

			// Extract all terms
			List<String> objects = extractor.extractList(valueString);
			for (String object : objects) {
				// Check syntax
//				if (syntaxChecker != null
//						&& !FactComponent.asJavaString(object).matches(
//								syntaxChecker)) {
//					Announce.debug("Extraction", object, "for", entity,
//							relation, "does not match syntax check",
//							syntaxChecker);
//					continue;
//				}
				// Check data type
				if (FactComponent.isLiteral(object) && i == 0) {
					String[] value = FactComponent
							.literalAndDatatypeAndLanguage(object);
					if (value.length != 2
							|| !factCollection.isSubClassOf(value[1], cls)
							&& !(value.length == 1 && cls.equals(YAGO.string))) {
						Announce.debug("Extraction", object, "for", entity,
								relation, "does not match typecheck", cls);
						continue;
					}
					FactComponent.setDataType(object, cls);
				}

				// check if the relation is <holdsPoliticalPosition>, then map arg2 to particular wordnet position.
				if(relation.equals("<holdsPoliticalPosition>")){
					object= getWordnetClassForPoliticalPosition(object, preferredMeanings);
					if(object==null)
						continue;
				}
				
				if (inverse)
					write(TEMPORALDIRTYINFOBOXFACTS, new Fact(object, relation,
							entity), TEMPORALINFOBOXSOURCES,
							FactComponent.wikipediaURL(entity),
							"TemporalInfoboxExtractor: from " + valueString);
				else if (i == 0) {
					baseFact = new Fact(entity, relation, object);
					// baseFact.makeId();
					write(TEMPORALDIRTYINFOBOXFACTS, baseFact,
							TEMPORALINFOBOXSOURCES,
							FactComponent.wikipediaURL(entity),
							"TemporalInfoboxExtractor: from " + valueString);
				} else if (!baseFact.getRelation().equals("")) {

					Fact metafact = baseFact.metaFact(relation, object);
					write(TEMPORALDIRTYINFOBOXFACTS, metafact,
							TEMPORALINFOBOXSOURCES,
							FactComponent.wikipediaURL(entity),
							"TemporalInfoboxExtractor: from " + valueString);
				}

				if (factCollection.contains(relation, RDFS.type, YAGO.function))
					break;
			}
		}

	}

	/** normalizes an attribute name */
	public static String normalizeAttribute(String a) {
		return (a.trim().toLowerCase().replace("_", "").replace(" ", "")
				.replaceAll("\\d", ""));
	}

	public static String normalizeAttribute2(String a) {
		return (a.trim().toLowerCase().replace("_", "").replace(" ", ""));
	}

	/** reads an infobox */
	public static Map<String, Set<String>> readInfobox(Reader in,
			Map<String, String> combinations) throws IOException {
		Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
		Map<String, Set<String>> resultUnNormalized = new TreeMap<String, Set<String>>();

		while (true) {
			String attribute = FileLines.readTo(in, '=', '}').toString();
			String normalizedAttribute = normalizeAttribute(attribute);

			if (normalizedAttribute.length() == 0) {
				next: for (String code : combinations.keySet()) {
					StringBuilder val = new StringBuilder();
					for (String attr : code.split(">")) {
						int scanTo = attr.indexOf('<');
						if (scanTo != -1) {
							val.append(attr.substring(0, scanTo));
							String temp = attr.substring(scanTo + 1);
							String newVal = D.pick(resultUnNormalized
									.get(normalizeAttribute2(temp)));
							if (newVal == null)
								continue next;
							val.append(newVal);
						} else {
							val.append(attr);
						}
					}
					D.addKeyValue(resultUnNormalized,
							normalizeAttribute2(combinations.get(code)),
							val.toString(), TreeSet.class);
				}

				result.putAll(resultUnNormalized);

				return (result);
			}
			StringBuilder value = new StringBuilder();
			int c = InfoboxExtractor.readEnvironment(in, value);
			D.addKeyValue(result, normalizedAttribute, value.toString().trim(),
					TreeSet.class);
			D.addKeyValue(resultUnNormalized, normalizeAttribute2(attribute),
					value.toString().trim(), TreeSet.class);

			if (c == '}' || c == -1 || c == -2)
				break;
		}

		// Apply combinations
		// next: for (String code : combinations.keySet()) {
		// StringBuilder val = new StringBuilder();
		// for (String attribute : code.split(">")) {
		// int scanTo = attribute.indexOf('<');
		// if (scanTo != -1) {
		// val.append(attribute.substring(0, scanTo));
		// String temp = attribute.substring(scanTo + 1);
		// String newVal = D.pick(result.get(normalizeAttribute(temp)));
		// if (newVal == null)
		// continue next;
		// val.append(newVal);
		// } else {
		// val.append(attribute);
		// }
		// }
		// D.addKeyValue(result, normalizeAttribute(combinations.get(code)),
		// val.toString(), TreeSet.class);
		// }

		// Apply combinations
		next: for (String code : combinations.keySet()) {
			StringBuilder val = new StringBuilder();
			for (String attribute : code.split(">")) {
				int scanTo = attribute.indexOf('<');
				if (scanTo != -1) {
					val.append(attribute.substring(0, scanTo));
					String temp = attribute.substring(scanTo + 1);
					String newVal = D.pick(resultUnNormalized
							.get(normalizeAttribute2(temp)));
					if (newVal == null)
						continue next;
					val.append(newVal);
				} else {
					val.append(attribute);
				}
			}
			D.addKeyValue(resultUnNormalized,
					normalizeAttribute2(combinations.get(code)),
					val.toString(), TreeSet.class);
		}

		result.putAll(resultUnNormalized);
		return (result);

	}

	public static Map<String, Set<String>> infoboxPatterns(
			FactCollection infoboxFacts) {
		Map<String, Set<String>> patterns = new HashMap<String, Set<String>>();
		Announce.doing("Compiling infobox patterns");
		for (Fact fact : infoboxFacts.getFactsWithRelation("<_infoboxPattern>")) {
			D.addKeyValue(patterns,
					normalizeAttribute2(fact.getArgJavaString(1)),
					fact.getArg(2), TreeSet.class);
		}
		if (patterns.isEmpty()) {
			Announce.warning("No infobox patterns found");
		}
		Announce.done();
		return (patterns);
	}

	public TemporalInfoboxExtractor(File wikipedia) {
		super(wikipedia);
	}

	public static void main(String[] args) throws Exception {
		Announce.setLevel(Announce.Level.DEBUG);
		// new PatternHardExtractor(new File("./data")).extract(new
		// File("/var/tmp/test/facts"), "test");
		// new HardExtractor(new File("./basics2s/data")).extract(new
		// File("/var/tmp/test/facts"), "test");
		// new TemporalInfoboxExtractor(new
		// File("/var/tmp/test/wikitest.xml")).extract(new
		// File("/var/tmp/test/facts"), "Test on 1 wikipedia article");
		// new TemporalInfoboxExtractor(new
		// File("/var/tmp/Wikipedia_Archive/DavidBeckham.xml")).extract(new
		// File("/var/tmp/test/facts"), "Test on 1 wikipedia article");
		new TemporalInfoboxExtractor(
				new File("/home/jbiega/Downloads/wiki.xml")).extract(new File(
				"/home/jbiega/data/yago2s"), "Test on 1 wikipedia article");

	}
}
