Project target: "linux" loadPackages: {"com.squareup.okio:okio:3.6.0"}

type WordGroup
  words1: MutableMap(Int, String)
  words2: MutableMap(Int, String)
type GameData wordsGroups: MutableList::WordGroup

type Game
constructor Game fromFile::String wordsPerTest::Int = [

  readCardToLines = [
    text = FileSystem read: fromFile toPath
    text split: "\n"
  ]
  lines = readCardToLines do
  linesChunked = lines chunked: wordsPerTest

  fillGroups = [ chunk::List::String ->
    words1::MutableMap(Int, String) = #{}
    words2::MutableMap(Int, String) = #{}


    chunk forEachIndexed: [ i, line ->
      line trim != "" => [
        num = i + 1

        r = line split: ":"
        first = r at: 0 |> trim
        second = r at: 1 |> trim
        words1 at: num put: first
        words2 at: num put: second
      ]
    ]

    WordGroup words1: words1 words2: words2
  ]

  linesChunked forEach: [
    wordGroup = fillGroups chunk: it
    GameLoop print: wordGroup
  ]

  game = Game new
]

type PlayerAnswer num1: Int num2: Int
type GameLoop
constructor GameLoop print: wordGroup::WordGroup = [
  foreignGroup = wordGroup words1
  nativeGroup = wordGroup words2

  nativeColumn = nativeGroup values toMutableList
  foreignColumn = foreignGroup values toMutableList
  mut indexes = 0..<foreignColumn count |> toMutableList
  mut shuffledIndexes = indexes shuffled toMutableList

  getRightAnswersTable = [ indexes::List::Int, shuffledIndexes::List::Int ->
    result::MutableMap(Int, Int) = #{}

    indexes forEach: [
      result at: (shuffledIndexes at: it) + 1 put: it + 1
    ]

    result
  ]

  mut rightAnswers2 = getRightAnswersTable indexes: indexes shuffledIndexes: shuffledIndexes
  // "indexes is $indexes" echo
  // "shuffledIndexes is $shuffledIndexes" echo
  // "rightAnswers2 is $rightAnswers2" echo
  // "nativeColumn is $nativeColumn" echo
  // "foreignColumn is $foreignColumn" echo

  print2Columns = [
    nativeColumn forEachIndexed: [ i, nativeWord ->
      foreignWord = foreignColumn at: (shuffledIndexes at: i)
      num = i inc
      "$num $nativeWord:\t\t$num $foreignWord" echo
    ]
  ]

  checkAnswer = [ col::Int, answer::Int ->
    rightAnswer = rightAnswers2 at: col
    rightAnswer == answer
  ]

  readPlayerAnswer = [
    ans = Console readln
    num1 = ans at: 0 |> digitToInt
    num2 = ans at: 1 |> digitToInt
    PlayerAnswer num1: num1 num2: num2
  ]

  "\n game loop" echo
  mut lives = 5
  [(lives > 0) && (nativeColumn count > 0)] whileTrue: [
    print2Columns do

    // ans = Console readln
    // splitted = ans split: " "
    // num1 = splitted at: 0 |> trim toInt
    // num2 = splitted at: 1 |> trim toInt
    answer = readPlayerAnswer do
    num1 = answer num1
    num2 = answer num2
    elementsCount = nativeColumn count
    (elementsCount < num1) => [
        "wrong numbers! There is only $elementsCount elements" echo
    ] |=> [
      isRight = checkAnswer col: num1 answer: num2
      isRight => [
        "Right answer!" echo

        nativeColumn removeAt: num1 - 1
        foreignColumn removeAt: num1 - 1
        indexes removeAt: num1 - 1
        shuffledIndexes removeAt: num2 - 1

        decreaseAllItemsExcept0 = [x::MutableList::Int, deleted::Int -> x map: [ it >= deleted => it - 1 |=> it ] |> toMutableList]
        shuffledIndexes <- decreaseAllItemsExcept0 x: shuffledIndexes deleted: num1 - 1
        indexes <- decreaseAllItemsExcept0 x: indexes deleted: num1 - 1

        rightAnswers2 <- getRightAnswersTable indexes: (0..<shuffledIndexes count |> toList) shuffledIndexes: shuffledIndexes
      ] |=> [
        "Wrong answer!" echo
        lives <- lives dec
        "lives left: $lives" echo
      ]
    ]
    "---" echo
  ]
  (lives == 0 => "You looze!" |=> "You are Greek now ^_^ !") echo
]


game = Game
  fromFile: "greekColors.txt"
  wordsPerTest: 4
