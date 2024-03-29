package cs224n.assignments;

import cs224n.io.BioIETreebankReader;
import cs224n.io.GENIATreebankReader;
import cs224n.io.PennTreebankReader;
import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.parser.EnglishPennTreebankParseEvaluator;
import cs224n.util.*;
import cs224n.classify.ProbabilisticClassifier;
import cs224n.math.DoubleArrays;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Object.*;
/**
 * Harness for PCFG Parser project.
 *
 * @author Dan Klein
 */
public class PCFGParserTester {

  // Parser interface ===========================================================

  /**
   * Parsers are required to map sentences to trees.  How a parser is
   * constructed and trained is not specified.
   */
  public static interface Parser {
    public void train(List<Tree<String>> trainTrees);
    public Tree<String> getBestParse(List<String> sentence);
  }


    public static class Triple {
	
	public int A, B, C;

	public void clear(){
	    A = -1;
	    B = -1;
	    C = -1;
	}
	
	public void setUnary(int a){
	    A = a;
	    B = -1;
	    C = -1;
	}

	public void setBinary(int a, int b, int c){
	    A = a;
	    B = b;
	    C = c;
	}

	public boolean isBinary(){
	    return (A != -1 && B != -1 && C != -1);
	}
	
	public boolean isUnary(){
	    return (A != -1 && B == -1 && C == -1);
	}
	
	public Triple(){
	    clear();
	}
	
	public Triple(int a){
	    setUnary(a);
	}

	public Triple(int a, int b, int c){
	    setBinary(a,b,c);
	}
	
	public String toString(){
	    return "("+A+","+B+","+C+")";
	}

    }

  // PCFGParser =================================================================

  /**
   * The PCFG Parser you will implement.
   */
  public static class PCFGParser implements Parser {
    
    private Grammar grammar;
    private Lexicon lexicon;
      private String[] tags;
      private Map<String, Integer> tagsL;
      private int num;
  public void train(List<Tree<String>> trainTrees) {
      
	List<Tree<String>> binarizedTrainingTrees = new ArrayList<Tree<String>>();
	
	//Binarize Trees
	for(Tree<String> trainTree : trainTrees){
	    
	    binarizedTrainingTrees.add(TreeAnnotations.annotateTree(trainTree));
	    
	}

	lexicon = new Lexicon(binarizedTrainingTrees);
	grammar = new Grammar(binarizedTrainingTrees);

	//Generate list of nonterminals
	Set<String> tagSet = new HashSet<String>();
	tagSet.add("ROOT");
	tagSet.addAll(grammar.unaryRulesByChild.keySet());
	tagSet.addAll(grammar.binaryRulesByRightChild.keySet());
	tagSet.addAll(grammar.binaryRulesByLeftChild.keySet());
	int size_g = tagSet.size();
	tagsL = new HashMap<String, Integer>();

	tags = new String[size_g];
	tagSet.toArray(tags);
	
	for(int i = 0; i < size_g; i++){
	    Integer I = new Integer(i);
	    tagsL.put(tags[i], I);
	}
    }

      public void printInfo(){
	  System.out.println("-------INF0------");
	  System.out.println("Unary Size: " +
			     grammar.unaryRulesByChild.keySet().size());
	  System.out.println("Bi R  Size: " +
			     grammar.binaryRulesByRightChild.keySet().size());
	  System.out.println("Bi L  Size: " +
			     grammar.binaryRulesByLeftChild.keySet().size());
	  System.out.println("Tags  Size: " + tags.length);
	  System.out.println("-----------------");
	  
      }
    
    public Tree<String> getBestParse(List<String> sentence) {
    	
	//Size of sentence
	int size = sentence.size();
	int size_g = tags.length;
	
	//Create SCORE double array and initialize
	double [][][] score = new double[size+1][size+1][size_g];
	for(int i = 0; i <= size; i++)
	    for(int j = 0; j <= size; j++)
		DoubleArrays.initialize(score[i][j], 0.0);

	//Create BACK double array and initialize
	Triple [][][] back = new Triple[size+1][size+1][size_g];
	for(int i = 0; i <= size; i++)
	    for(int j = i+1; j <= size; j++)
		for(int k = 0; k < size_g; k++)
		    back[i][j][k] = new Triple();

	//First populate diagonals with appropriate tags
	for(int i = 0; i < size; i++){
	    for(int j = 0; j < size_g; j++){
		double prob = lexicon.scoreTagging(sentence.get(i), tags[j]);
		if(prob > 0)
		    score[i][i+1][j] = prob;
	    }
	    handleUnaries(score,back,tags,i,i+1);
	}

	//Populate rest of the table
	for(int span = 2; span <= size; span++){
	    for(int begin = 0; begin <= size - span; begin++){
		int end = begin + span;
		for(int split = begin + 1; split <= end - 1; split++){
		    for(int C = 0; C < size_g; C++){
			HashMap<Integer,BinaryRule> RR = grammar.getBinaryRulesByRightChild(tags[C]);
			Iterator itr = RR.values().iterator();
			while(itr.hasNext()){
			    BinaryRule rule = (BinaryRule)itr.next();
			    String left = rule.getLeftChild();
			    if(grammar.getBinaryRulesByLeftChild(left).get(rule.hashCode())!=null){
				int A = tagsL.get(rule.getParent());
				int B = tagsL.get(left);
				double prob = score[begin][split][B] *
				    score[split][end][C] * rule.getScore();
				if(prob > score[begin][end][A]){
				    score[begin][end][A] = prob;
				    back[begin][end][A].setBinary(split,B,C);
				}
			    }
			}
		    }
       		}
		handleUnaries(score,back,tags,begin,end);		    
	    }
	}

	//Output Table
	//printTable(score,sentence);	

	//Build Tree and return it
	return buildTree(score,back,sentence);
    }

      private Tree<String> buildTree(double[][][] score, Triple[][][]
	  back, List<String> sentence){

	  int size = score.length;
	  //create tree
	  Tree<String> tree = new Tree("ROOT");
	  
	  //Get best node to start at
	  int bestTag = cs224n.math.DoubleArrays.argMax(score[0][size-1]);
	      //tagsL.get("S");
	  
	  //Create and set children
	  List<Tree<String>> children = new ArrayList<Tree<String>>();
	  children.add(buildTreeHelper(score,back,sentence,0,size-1,bestTag,0));
	  tree.setChildren(children);
	  
	  //return tree
	  return TreeAnnotations.unAnnotateTree(tree);
      }

      private Tree<String> buildTreeHelper(double[][][] score,
					   Triple[][][] back,
					   List<String> sentence,
					   int A,
					   int B,
					   int C,
					   int d){

	  //Get tag and backtrace
	  String tag = tags[C];
	  Triple node = back[A][B][C];

	  //Create new subtree with tag
       	  Tree<String> subTree = new Tree<String>(tags[C]);
	  List<Tree<String>> children = new ArrayList<Tree<String>>();
	  
	  //Set Children
	  if(node.isUnary()){
	      children.add(buildTreeHelper(score,back,sentence,A,B,node.A,d+1));
	  } else if(node.isBinary()){
	      
	      children.add(buildTreeHelper(score,back,sentence,A,node.A,node.B,d+1));
	      children.add(buildTreeHelper(score,back,sentence,node.A,B,node.C,d+1));
	  } else {
	      children.add(new Tree<String>(sentence.get(A)));
	  }
	  //Add Children
	  subTree.setChildren(children);

	  //Return subtree
	  return subTree;
      }

      private void handleUnaries(double[][][] score,
				 Triple[][][] back,
				 String[] terminals,
				 int begin,
				 int end){
	  //Handle Unaries
	  int size_g = terminals.length;
	  boolean added = true;
	  while(added){
	      added = false;
	      for(int B = 0; B < size_g; B++){
		  HashMap<Integer,UnaryRule> rules = grammar.getUnaryRulesByChild(terminals[B]);
		  Iterator itr = rules.values().iterator();
		  while(itr.hasNext()){
		      UnaryRule rule = (UnaryRule)itr.next();
		      double prob = rule.getScore() *
			  score[begin][end][B];
		      int A = tagsL.get(rule.getParent());
		      if(prob > score[begin][end][A]){
			  score[begin][end][A] = prob;
			  back[begin][end][A].setUnary(B);
			  added = true;
		      }
		  }
	      }
	  }
      }

      private void printTable(double[][][] table,List<String> sentence){
	  int size = table.length-1;
	  int size_g = table[0][0].length;
	  //TESTING OUTPUT
	  System.out.println("-------Table---------");
	  for(int j = 0; j <= size; j++){
	      if(j != 0){
		  System.out.printf("%-2s",j);
		  System.out.printf("%-13s",sentence.get(j-1));
	      } else {
		  System.out.printf("%-25s","-");
	      }
	  }
	  System.out.println();
	  for(int p = 0; p < 20 * size; p++)
	      System.out.printf("-");
	  System.out.println();
	  
	  for(int i = 0; i < size; i++){
	      System.out.printf("%-10s",i);
	      for(int k = 0; k < size_g; k++){
		  if(k != 0)
		      for(int p = 0; p < 10; p++)
			  System.out.printf(" ");
		  System.out.printf("%-15s=",tags[k]);
		  for(int j = 0; j < size; j++){
		      System.out.printf("%-10.8f",table[i][j+1][k]);
		      System.out.printf("     ");
		  }
		  System.out.println();
	      }
	      for(int p = 0; p <= 20 * (size+1); p++)
		  System.out.printf("-");
	      System.out.println();
	  }
	  System.out.println("");
	  System.out.println("--------------");
      }
  }


  // BaselineParser =============================================================

  /**
   * Baseline parser (though not a baseline I've ever seen before).  Tags the
   * sentence using the baseline tagging method, then either retrieves a known
   * parse of that tag sequence, or builds a right-branching parse for unknown
   * tag sequences.
   */
  public static class BaselineParser implements Parser {

    CounterMap<List<String>,Tree<String>> knownParses;
    CounterMap<Integer,String> spanToCategories;
    Lexicon lexicon;

    public void train(List<Tree<String>> trainTrees) {
      lexicon = new Lexicon(trainTrees);
      knownParses = new CounterMap<List<String>, Tree<String>>();
      spanToCategories = new CounterMap<Integer, String>();
      for (Tree<String> trainTree : trainTrees) {
        List<String> tags = trainTree.getPreTerminalYield();
        knownParses.incrementCount(tags, trainTree, 1.0);
        tallySpans(trainTree, 0);
      }
    }

    public Tree<String> getBestParse(List<String> sentence) {
      List<String> tags = getBaselineTagging(sentence);
      if (knownParses.keySet().contains(tags)) {
        return getBestKnownParse(tags, sentence);
      }
      return buildRightBranchParse(sentence, tags);
    }

    /* Builds a tree that branches to the right.  For pre-terminals it
     * uses the most common tag for the word in the training corpus.
     * For all other non-terminals it uses the tag that is most common
     * in training corpus of tree of the same size span as the tree
     * that is being labeled. */
    private Tree<String> buildRightBranchParse(List<String> words, List<String> tags) {
      int currentPosition = words.size() - 1;
      Tree<String> rightBranchTree = buildTagTree(words, tags, currentPosition);
      while (currentPosition > 0) {
        currentPosition--;
        rightBranchTree = merge(buildTagTree(words, tags, currentPosition),
                                rightBranchTree);
      }
      rightBranchTree = addRoot(rightBranchTree);
      return rightBranchTree;
    }

    private Tree<String> merge(Tree<String> leftTree, Tree<String> rightTree) {
      int span = leftTree.getYield().size() + rightTree.getYield().size();
      String mostFrequentLabel = spanToCategories.getCounter(span).argMax();
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      children.add(leftTree);
      children.add(rightTree);
      return new Tree<String>(mostFrequentLabel, children);
    }

    private Tree<String> addRoot(Tree<String> tree) {
      return new Tree<String>("ROOT", Collections.singletonList(tree));
    }

    private Tree<String> buildTagTree(List<String> words,
                                      List<String> tags,
                                      int currentPosition) {
      Tree<String> leafTree = new Tree<String>(words.get(currentPosition));
      Tree<String> tagTree = new Tree<String>(tags.get(currentPosition), 
                                              Collections.singletonList(leafTree));
      return tagTree;
    }
    
    private Tree<String> getBestKnownParse(List<String> tags, List<String> sentence) {
      Tree<String> parse = knownParses.getCounter(tags).argMax().deepCopy();
      parse.setWords(sentence);
      return parse;
    }

    private List<String> getBaselineTagging(List<String> sentence) {
      List<String> tags = new ArrayList<String>();
      for (String word : sentence) {
        String tag = getBestTag(word);
        tags.add(tag);
      }
      return tags;
    }

    private String getBestTag(String word) {
      double bestScore = Double.NEGATIVE_INFINITY;
      String bestTag = null;
      for (String tag : lexicon.getAllTags()) {
        double score = lexicon.scoreTagging(word, tag);
        if (bestTag == null || score > bestScore) {
          bestScore = score;
          bestTag = tag;
        }
      }
      return bestTag;
    }

    private int tallySpans(Tree<String> tree, int start) {
      if (tree.isLeaf() || tree.isPreTerminal()) 
        return 1;
      int end = start;
      for (Tree<String> child : tree.getChildren()) {
        int childSpan = tallySpans(child, end);
        end += childSpan;
      }
      String category = tree.getLabel();
      if (! category.equals("ROOT"))
        spanToCategories.incrementCount(end - start, category, 1.0);
      return end - start;
    }

  }


  // TreeAnnotations ============================================================

  /**
   * Class which contains code for annotating and binarizing trees for
   * the parser's use, and debinarizing and unannotating them for
   * scoring.
   */
  public static class TreeAnnotations {

      public static Tree<String> annotateTree(Tree<String>
					      unAnnotatedTree) {

      // Currently, the only annotation done is a lossless binarization

      // TODO: change the annotation from a lossless binarization to a
      // finite-order markov process (try at least 1st and 2nd order)
	/*
	System.out.println("-------------------");
	System.out.println(cs224n.ling.Trees.PennTreeRenderer.render(unAnnotatedTree));
	*/
	//Stack<Tree<String>> tags = new Stack<Tree<String>>();
	List<String> parents = new ArrayList<String>();
	int h = 1;
	int w = 2;

       	markParents(unAnnotatedTree, parents, h);
	return binarizeTree(unAnnotatedTree,w);
    }

      private static void markParents(Tree<String> tree, List<String>
				      parents, int h){
	  
	  if(tree.isPreTerminal()) return;


	  String parent = tree.getLabel();
	  List<Tree<String>> children = tree.getChildren();
	  Iterator itr = children.iterator();

	  
	  String newLabel = parent;
	  for(int i = parents.size()-1; (i > parents.size() - 1 - h
					 && i >= 0);
	      i--){
	      String child = parents.get(i);
	      newLabel += "-^" + child;
	  } 
	  tree.setLabel(newLabel);
	 
	  parents.add(parent);
	  while(itr.hasNext()){
	      Tree<String> child = (Tree<String>)itr.next();
	      markParents(child, parents, h);
	  }
      }
     
      private static Tree<String> binarizeTree(Tree<String> tree, int h) {
	  String label = tree.getLabel();
	  if (tree.isLeaf())
	      return new Tree<String>(label);
	  if (tree.getChildren().size() == 1) {
	      return new Tree<String>
		  (label, 
		   Collections.singletonList(binarizeTree(tree.getChildren().get(0),h)));
	  }
	  // otherwise, it's a binary-or-more local tree, 
	  // so decompose it into a sequence of binary and unary trees.
	  String baseLabel = "@"+label+"->";
	  Tree<String> intermediateTree = binarizeTreeHelper(tree, 0, baseLabel,h);
	  return new Tree<String>(label, intermediateTree.getChildren());
      }
      
      private static Tree<String> binarizeTreeHelper(Tree<String> tree,
						     int numChildrenGenerated, 
						     String baseLabel,
						     int h) {
	  Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
	  List<Tree<String>> children = new ArrayList<Tree<String>>();

	  String intermediateLabel = "";
	  for(int i = numChildrenGenerated - 1; 
	      (i > numChildrenGenerated - 1 - h) && (i >= 0);
	      i--){
	      intermediateLabel = "_" + tree.getChildren().get(i).getLabel() + intermediateLabel;
	  }
	  intermediateLabel = baseLabel + intermediateLabel;


	  children.add(binarizeTree(leftTree,h));
	  if (numChildrenGenerated < tree.getChildren().size() - 1) {
	      Tree<String> rightTree = binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							  baseLabel,h);
	      children.add(rightTree);
	  }
	  return new Tree<String>(intermediateLabel, children);
      } 
      
    public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

      // Remove intermediate nodes (labels beginning with "@"
      // Remove all material on node labels which follow their base symbol 
      // (cuts at the leftmost -, ^, or : character)
      // Examples: a node with label @NP->DT_JJ will be spliced out, 
     // and a node with label NP^S will be reduced to NP

      Tree<String> debinarizedTree =
        Trees.spliceNodes(annotatedTree, new Filter<String>() {
          public boolean accept(String s) {
            return s.startsWith("@");
          }
        });
      Tree<String> unAnnotatedTree = 
        (new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
      return unAnnotatedTree;
    }
  }


  // Lexicon ====================================================================

  /**
   * Simple default implementation of a lexicon, which scores word,
   * tag pairs with a smoothed estimate of P(tag|word)/P(tag).
   */
  public static class Lexicon {

    CounterMap<String,String> wordToTagCounters = new CounterMap<String, String>();
    double totalTokens = 0.0;
    double totalWordTypes = 0.0;
    Counter<String> tagCounter = new Counter<String>();
    Counter<String> wordCounter = new Counter<String>();
    Counter<String> typeTagCounter = new Counter<String>();

    public Set<String> getAllTags() {
      return tagCounter.keySet();
    }

    public boolean isKnown(String word) {
      return wordCounter.keySet().contains(word);
    }

    /* Returns a smoothed estimate of P(word|tag) */
    public double scoreTagging(String word, String tag) {
      double p_tag = tagCounter.getCount(tag) / totalTokens;
      double c_word = wordCounter.getCount(word);
      double c_tag_and_word = wordToTagCounters.getCount(word, tag);
      if (c_word < 10) { // rare or unknown
        c_word += 1.0;
        c_tag_and_word += typeTagCounter.getCount(tag) / totalWordTypes;
      }
      double p_word = (1.0 + c_word) / (totalTokens + totalWordTypes);
      double p_tag_given_word = c_tag_and_word / c_word;
      return p_tag_given_word / p_tag * p_word;
    }

    /* Builds a lexicon from the observed tags in a list of training trees. */
    public Lexicon(List<Tree<String>> trainTrees) {
      for (Tree<String> trainTree : trainTrees) {
        List<String> words = trainTree.getYield();
        List<String> tags = trainTree.getPreTerminalYield();
        for (int position = 0; position < words.size(); position++) {
          String word = words.get(position);
          String tag = tags.get(position);
          tallyTagging(word, tag);
        }
      }
    }

    private void tallyTagging(String word, String tag) {
      if (! isKnown(word)) {
        totalWordTypes += 1.0;
        typeTagCounter.incrementCount(tag, 1.0);
      }
      totalTokens += 1.0;
      tagCounter.incrementCount(tag, 1.0);
      wordCounter.incrementCount(word, 1.0);
      wordToTagCounters.incrementCount(word, tag, 1.0);
    }
  }


  // Grammar ====================================================================

  /**
   * Simple implementation of a PCFG grammar, offering the ability to
   * look up rules by their child symbols.  Rule probability estimates
   * are just relative frequency estimates off of training trees.
   */
  public static class Grammar {

      Map<String, HashMap<Integer,BinaryRule>> binaryRulesByLeftChild = 
	new HashMap<String, HashMap<Integer, BinaryRule>>();
      Map<String, HashMap<Integer,BinaryRule>> binaryRulesByRightChild = 
	new HashMap<String, HashMap<Integer, BinaryRule>>();
      Map<String, HashMap<Integer,UnaryRule>> unaryRulesByChild = 
	new HashMap<String, HashMap<Integer, UnaryRule>>();

    /* Rules in grammar are indexed by child for easy access when
     * doing bottom up parsing. */
      public HashMap<Integer, BinaryRule> getBinaryRulesByLeftChild(String leftChild) {
	  return getValueHelper(binaryRulesByLeftChild, leftChild);
      }

      public HashMap<Integer, BinaryRule> getBinaryRulesByRightChild(String rightChild) {
	  return getValueHelper(binaryRulesByRightChild, rightChild);
      }

      public HashMap<Integer, UnaryRule> getUnaryRulesByChild(String child) {
	  return getValueHelper(unaryRulesByChild, child);
      }

      public <K,V> HashMap<Integer,V> getValueHelper(Map<K,HashMap<Integer,V>> map, K key) {
	  HashMap<Integer,V> valueMap = map.get(key);
	  if (valueMap == null) return new HashMap<Integer,V>();
	  return valueMap;
      }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      List<String> ruleStrings = new ArrayList<String>();
      for (String leftChild : binaryRulesByLeftChild.keySet()) {
	  for (BinaryRule binaryRule : getBinaryRulesByLeftChild(leftChild).values()) {
          ruleStrings.add(binaryRule.toString());
        }
      }
      for (String child : unaryRulesByChild.keySet()) {
	  for (UnaryRule unaryRule : getUnaryRulesByChild(child).values()) {
          ruleStrings.add(unaryRule.toString());
        }
      }
      for (String ruleString : CollectionUtils.sort(ruleStrings)) {
        sb.append(ruleString);
        sb.append("\n");
      }
      return sb.toString();
    }


      public <K,V> void addRuleHelper(Map<K, HashMap<Integer,V>> map, K key, V value) {
	  HashMap<Integer,V> valueMap = map.get(key);
	  if (valueMap == null) {
	      valueMap = new HashMap<Integer,V>();
	      map.put(key, valueMap);
	  }
	  valueMap.put(new Integer(value.hashCode()), value);
      }

    private void addBinary(BinaryRule binaryRule) {
	
	addRuleHelper(binaryRulesByLeftChild, binaryRule.getLeftChild(),
		      binaryRule);
	addRuleHelper(binaryRulesByRightChild, binaryRule.getRightChild(), binaryRule);
	
	//binaryRulesByLeftChild.put(binaryRule.hashCode(), binaryRule);
	//	binaryRulesByLeftChild.put(binaryRule.hashCode(), binaryRule);

	/*
	  CollectionUtils.addToValueList(binaryRulesByLeftChild, 
                                     binaryRule.getLeftChild(), binaryRule);
				     CollectionUtils.addToValueList(binaryRulesByRightChild, 
                                     binaryRule.getRightChild(),
	binaryRule);
	*/
    }

    private void addUnary(UnaryRule unaryRule) {

	addRuleHelper(unaryRulesByChild, unaryRule.getChild(), unaryRule);
	/*
	  CollectionUtils.addToValueList(unaryRulesByChild, 
                                     unaryRule.getChild(), unaryRule);
	*/
    }

    /* A builds PCFG using the observed counts of binary and unary
     * productions in the training trees to estimate the probabilities
     * for those rules.  */ 
    public Grammar(List<Tree<String>> trainTrees) {
      Counter<UnaryRule> unaryRuleCounter = new Counter<UnaryRule>();
      Counter<BinaryRule> binaryRuleCounter = new Counter<BinaryRule>();
      Counter<String> symbolCounter = new Counter<String>();
      for (Tree<String> trainTree : trainTrees) {
        tallyTree(trainTree, symbolCounter, unaryRuleCounter, binaryRuleCounter);
      }
      for (UnaryRule unaryRule : unaryRuleCounter.keySet()) {
        double unaryProbability = 
          unaryRuleCounter.getCount(unaryRule) / 
          symbolCounter.getCount(unaryRule.getParent());
        unaryRule.setScore(unaryProbability);
        addUnary(unaryRule);
      }
      for (BinaryRule binaryRule : binaryRuleCounter.keySet()) {
        double binaryProbability = 
          binaryRuleCounter.getCount(binaryRule) / 
          symbolCounter.getCount(binaryRule.getParent());
        binaryRule.setScore(binaryProbability);
        addBinary(binaryRule);
      }
    }

    private void tallyTree(Tree<String> tree, Counter<String> symbolCounter,
                           Counter<UnaryRule> unaryRuleCounter, 
                           Counter<BinaryRule> binaryRuleCounter) {
      if (tree.isLeaf()) return;
      if (tree.isPreTerminal()) return;
      if (tree.getChildren().size() == 1) {
        UnaryRule unaryRule = makeUnaryRule(tree);
        symbolCounter.incrementCount(tree.getLabel(), 1.0);
        unaryRuleCounter.incrementCount(unaryRule, 1.0);
      }
      if (tree.getChildren().size() == 2) {
        BinaryRule binaryRule = makeBinaryRule(tree);
        symbolCounter.incrementCount(tree.getLabel(), 1.0);
        binaryRuleCounter.incrementCount(binaryRule, 1.0);
      }
      if (tree.getChildren().size() < 1 || tree.getChildren().size() > 2) {
        throw new RuntimeException("Attempted to construct a Grammar with an illegal tree: "+tree);
      }
      for (Tree<String> child : tree.getChildren()) {
        tallyTree(child, symbolCounter, unaryRuleCounter,  binaryRuleCounter);
      }
    }

    private UnaryRule makeUnaryRule(Tree<String> tree) {
      return new UnaryRule(tree.getLabel(), tree.getChildren().get(0).getLabel());
    }

    private BinaryRule makeBinaryRule(Tree<String> tree) {
      return new BinaryRule(tree.getLabel(), tree.getChildren().get(0).getLabel(), 
                            tree.getChildren().get(1).getLabel());
    }
  }


  // BinaryRule =================================================================

  /* A binary grammar rule with score representing its probability. */
  public static class BinaryRule {

    String parent;
    String leftChild;
    String rightChild;
    double score;

    public String getParent() {
      return parent;
    }

    public String getLeftChild() {
      return leftChild;
    }

    public String getRightChild() {
      return rightChild;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BinaryRule)) return false;

      final BinaryRule binaryRule = (BinaryRule) o;

      if (leftChild != null ? !leftChild.equals(binaryRule.leftChild) : binaryRule.leftChild != null) 
        return false;
      if (parent != null ? !parent.equals(binaryRule.parent) : binaryRule.parent != null) 
        return false;
      if (rightChild != null ? !rightChild.equals(binaryRule.rightChild) : binaryRule.rightChild != null) 
        return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (parent != null ? parent.hashCode() : 0);
      result = 29 * result + (leftChild != null ? leftChild.hashCode() : 0);
      result = 29 * result + (rightChild != null ? rightChild.hashCode() : 0);
      return result;
    }

    public String toString() {
      return parent + " -> " + leftChild + " " + rightChild + " %% "+score;
    }

    public BinaryRule(String parent, String leftChild, String rightChild) {
      this.parent = parent;
      this.leftChild = leftChild;
      this.rightChild = rightChild;
    }
  }


  // UnaryRule ==================================================================

  /** A unary grammar rule with score representing its probability. */
  public static class UnaryRule {

    String parent;
    String child;
    double score;

    public String getParent() {
      return parent;
    }

    public String getChild() {
      return child;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UnaryRule)) return false;

      final UnaryRule unaryRule = (UnaryRule) o;

      if (child != null ? !child.equals(unaryRule.child) : unaryRule.child != null) return false;
      if (parent != null ? !parent.equals(unaryRule.parent) : unaryRule.parent != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (parent != null ? parent.hashCode() : 0);
      result = 29 * result + (child != null ? child.hashCode() : 0);
      return result;
    }

    public String toString() {
      return parent + " -> " + child + " %% "+score;
    }

    public UnaryRule(String parent, String child) {
      this.parent = parent;
      this.child = child;
    }
  }


  // PCFGParserTester ===========================================================

  // Longest sentence length that will be tested on.
  private static int MAX_LENGTH = 20;

  private static void testParser(Parser parser, List<Tree<String>> testTrees) {
    testParser(parser, testTrees, null);
  }
  
  private static void testParser(Parser parser, List<Tree<String>> testTrees, ProbabilisticClassifier nerClassifier) {
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = 
      new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>
      (Collections.singleton("ROOT"), 
       new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();

      if (nerClassifier != null) {
        System.err.println(testTree);
        System.err.println("test sentence: "+testSentence);
        List<Pair<String,String>> chunkedSentence = MaximumEntropyClassifierTester.labelAndChunkSequence(nerClassifier, testSentence);
        testSentence = new ArrayList<String>();
        for (Pair<String,String> chunkAndLabel : chunkedSentence) {
          testSentence.add(chunkAndLabel.getFirst());
        }
        System.err.println("chunked sentence: "+testSentence);
      }
      
      if (testSentence.size() > MAX_LENGTH)
        continue;

      Tree<String> guessedTree = parser.getBestParse(testSentence);
      System.out.println("Guess:\n"+Trees.PennTreeRenderer.render(guessedTree));
      System.out.println("Gold:\n"+Trees.PennTreeRenderer.render(testTree));
      eval.evaluate(guessedTree, testTree);
    }
    eval.display(true);
    ((PCFGParser)parser).printInfo();
  }
  
  private static List<Tree<String>> readTrees(String basePath, int low,
			int high) {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath,
				low, high);
		// normalize trees
		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			// System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
			normalizedTreeList.add(normalizedTree);
		}
		return normalizedTreeList;
	}

	private static List<Tree<String>> readTrees(String basePath) {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath);
		// normalize trees
		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
	  List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      //      System.err.println(tree);
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      // System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }

  
  private static List<Tree<String>> readGENIATrees(String basePath, int low, int high) {
    Collection<Tree<String>> trees = GENIATreebankReader.readTrees(basePath, low, high);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      // System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }

  private static List<Tree<String>> readGENIATrees(String basePath) {
    Collection<Tree<String>> trees = GENIATreebankReader.readTrees(basePath);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      //        System.err.println(tree);
      System.out.println("Tree: "+tree);
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      // System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }
  
  private static List<Tree<String>> readBioIETrees(String basePath, int low, int high) {
    Collection<Tree<String>> trees = BioIETreebankReader.readTrees(basePath, low, high);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      // System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }

  private static List<Tree<String>> readBioIETrees(String basePath) {
    Collection<Tree<String>> trees = BioIETreebankReader.readTrees(basePath);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      //        System.err.println(tree);
      System.out.println("Tree: "+tree);
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      // System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }
  
  public static void main(String[] args) {

    // set up default options ..............................................
    Map<String, String> options = new HashMap<String, String>();
    options.put("-path",      "/afs/ir/class/cs224n/pa3/data/parser/");
    options.put("-data",      "ptb");
    options.put("-parser",    "cs224n.assignments.PCFGParserTester$PCFGParser");
    options.put("-maxLength", "20");

    // let command-line options supersede defaults .........................
    options.putAll(CommandLineUtils.simpleCommandLineParser(args));
    System.out.println("PCFGParserTester options:");
    for (Map.Entry<String, String> entry: options.entrySet()) {
      System.out.printf("  %-12s: %s%n", entry.getKey(), entry.getValue());
    }
    System.out.println();

    MAX_LENGTH = Integer.parseInt(options.get("-maxLength"));

    Parser parser;
    try {
      Class parserClass = Class.forName(options.get("-parser"));
      parser = (Parser) parserClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    System.out.println("Using parser: " + parser);

    String basePath = options.get("-path");
    String preBasePath = basePath;
    String dataSet = options.get("-data");
    if (!basePath.endsWith("/"))
      basePath += "/";
    //basePath += dataSet;
    System.out.println("Data will be loaded from: " + basePath + "\n");

    List<Tree<String>> trainTrees = new ArrayList<Tree<String>>(),
    				   validationTrees = new ArrayList<Tree<String>>(),
    				   testTrees = new ArrayList<Tree<String>>();

    if (dataSet.equals("miniTest")) {
      // training data: first 3 of 4 datums
      basePath += "parser/"+dataSet;
      System.out.println("Loading training trees...");
      trainTrees = readTrees(basePath, 1, 3);
      System.out.println("done.");

      // test data: last of 4 datums
      System.out.println("Loading test trees...");
      testTrees = readTrees(basePath, 4, 4);
      System.out.println("done.");

    }
    if (dataSet.equals("genia") || dataSet.equals("combo")) {
      basePath += "parser/";
      // training data: GENIA Treebank 0-90%
      System.out.println("Loading GENIA training trees... from: "+basePath+"genia");
      trainTrees.addAll(readGENIATrees(basePath+"genia", 0, 440));
      System.out.println("done.");
      System.out.println("Train trees size: "+trainTrees.size());

      System.out.println("First train tree: "+Trees.PennTreeRenderer.render(trainTrees.get(0)));
      System.out.println("Last train tree: "+Trees.PennTreeRenderer.render(trainTrees.get(trainTrees.size()-1)));
      
      // validation data: GENIA Treebank 90-95%
      // System.out.println("Loading validation trees...");
      // validationTrees.add(readGENIATrees(basePath+"genia", 441, 480));
      // System.out.println("Test trees size: "+testTrees.size());
      // System.out.println("done.");

      // test data: GENIA Treebank 95-100%
      System.out.println("Loading GENIA test trees...");
      testTrees.addAll(readGENIATrees(basePath+"genia", 481, 500));
      System.out.println("Test trees size: "+testTrees.size());
      System.out.println("done.");
      
      System.out.println("First train tree: "+Trees.PennTreeRenderer.render(testTrees.get(0)));
      System.out.println("Last train tree: "+Trees.PennTreeRenderer.render(testTrees.get(testTrees.size()-1)));
    }
    if (dataSet.equals("bioie") || dataSet.equals("combo")) {
      if ( !dataSet.equals("combo"))
        basePath += "parser/";
      // training data: BioIE Treebank 0-90%
      System.out.println("Loading BioIE training trees...");
      trainTrees.addAll(readBioIETrees(basePath+"bioie", 0, 580));
      System.out.println("done.");
      System.out.println("Train trees size: "+trainTrees.size());
      
      System.out.println("First train tree: "+Trees.PennTreeRenderer.render(trainTrees.get(0)));
      System.out.println("Last train tree: "+Trees.PennTreeRenderer.render(trainTrees.get(trainTrees.size()-1)));

      // validation data: BioIE Treebank 90-95%
      // System.out.println("Loading validation trees...");
      // validationTrees.addAll(readBioIETrees(basePath+"bioie", 581, 613));
      // System.out.println("Test trees size: "+testTrees.size());
      // System.out.println("done.");

      // test data: BioIE Treebank 95-100%
      System.out.println("Loading BioIE test trees...");
      testTrees.addAll(readBioIETrees(basePath+"bioie", 613, 645));
      System.out.println("Test trees size: "+testTrees.size());
      System.out.println("done.");

      System.out.println("First train tree: "+Trees.PennTreeRenderer.render(testTrees.get(0)));
      System.out.println("Last train tree: "+Trees.PennTreeRenderer.render(testTrees.get(testTrees.size()-1)));
      
    }
    if (!dataSet.equals("miniTest") && !dataSet.equals("genia") && !dataSet.equals("bioie") && !dataSet.equals("combo")){
      throw new RuntimeException("Bad data set: " + dataSet + ": use miniTest, genia, bioie, or combo (genia and bioie)."); 
    }
    if ( options.containsKey("-testData") ) {
      System.out.println("Loading "+options.get("-testData")+" test trees ...");
      testTrees.clear();
      testTrees = readTrees(preBasePath+"parser/"+options.get("-testData"));
      System.out.println("Test trees size: "+testTrees.size());
    }

    System.out.println("\nTraining parser...");
    parser.train(trainTrees);

    ProbabilisticClassifier nerClassifier = null;
    if (options.get("-nerTrainFile") != null) {
      System.out.println("\nTraining NER classifier...");
      try {
        nerClassifier = MaximumEntropyClassifierTester.getClassifier(options.get("-nerTrainFile")+".train");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } 
 
    System.out.println("\nTesting parser...");
    testParser(parser, testTrees, nerClassifier);
  }
}
