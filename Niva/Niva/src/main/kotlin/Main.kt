@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex

import main.utils.CompilerRunner
import main.utils.compileProjFromFile
import java.io.*
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.utils.ArgsManager
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.daemon
import main.utils.getSpecialInfoArg
import main.utils.help
import main.utils.time
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}

const val newText = """
    // icon names close-symbolic, window-close-symbolic, mail-archive-symbolic
// addCssClass "linked"

Project use: "org.gnome.adw"

app = Application name: "my.app" 

type Person
  name: String
  age: Int

person = Person name: "String" age: 42
person age 

app onActivate: [

  window = (org.gnome.adw.ApplicationWindow app: app);
    title: "Dela";
    x: 280 y: 340 
  
  incButton = [
    mut n = 1 
    btn = (Button new); 
      label: "Hello Adw";
      hexpand: true; 
      vexpand: true;
      onClicked: [
        "clicked n times!" echo
        n <- n inc
      ]
    btn
  ]


  addNewProjectBtn = Button fromIconName: "tab-new-symbolic"
  overviewTabsBtn = Button fromIconName: "preferences-desktop-keyboard-shortcuts-symbolic" // org.gnome.Settings-multitasking-symbolic //"overview-symbolic"
  

  preferencePage = [
    createTaskConstructorRow = [ onApply:: [ String -> ] ->
      // layout: addGroup entry removeGroup

      addGroupBtn =    BtnFactory flatIcon: "list-add-symbolic" //Button fromIconName: "list-add-symbolic"
      removeGroupBtn = BtnFactory flatIcon: "window-close-symbolic"

      entryRow = (EntryRow new);
        showApplyButton: true;
        // addPrefix: addGroupBtn;
        addSuffix: removeGroupBtn;
        title: "Add new task";
        // heightRequest: 60;
        addCssClass: "transparent";
        addCssClass: "rounded-corners"
        


      entryRow; onApply: [ onApply String: entryRow text ]
    ]

    // сделать чтобы возвращала кастом объект который содержит все виджеты
    createTaskRow = [ text::String ->
      // label = Label str: text

      changeNameRow = (EntryRow new);
        showApplyButton: true;
        text: text;
        title: "Change task name"


      expRow = (ExpanderRow new); 
        addRow: changeNameRow;
        title: text
        // child: label
      
      changeNameRow onApply: [ expRow title: changeNameRow text ]
      
      expRow
    ]
    


    pp = PreferencesPage new

    createGroup = [
      model = Model widgetConstructor: createTaskRow
      pg = model widget

      ar1 = createTaskConstructorRow onApply: [taskName::String -> 
        "adding task: taskName" echo
        model add: taskName
      ]
      // ar1 subtitle: "mneieineinm"

      pg add: ar1;
        // add: ar2;
        // add: ar3;
        title: "hallou"

      pg
    ]

    pg1 = createGroup do
    pg2 = createGroup do
    pg3 = createGroup do

  
    pp add: pg1;
       add: pg2;
       add: pg3

  
  ] 


  mainBox = [
    mut pageCounter = 0
    newPageName = [ 
      pageCounter <- pageCounter inc
      "Page pageCounter" 
    ]

    header = (org.gnome.adw.HeaderBar new);
      packStart: addNewProjectBtn;
      packStart: overviewTabsBtn


    constructMainWidget = [preferencePage do]
    
     
    tabView = TabView new
    newPage = tabView append: constructMainWidget do
    newPage title: newPageName do

    tabBar = (TabBar new);
      view: tabView

    box = (Box orientation: Orientation.VERTICAL spacing: 0);
      append: header;
      append: tabBar;
      append: tabView



    overview = (TabOverview new); 
      enableNewTab: true;
      view: tabView;
      child: box
    
    // actions
    addNewProjectBtn onClicked: [
      page = tabView append: constructMainWidget do
      page title: newPageName do
    ]
    overview onCreateTab: [
      page = tabView append: constructMainWidget do
      page title: newPageName do 
      page
    ]

    overviewTabsBtn onClicked: [
      overview open: true
    ]


    tabView onClosePage: [page ->
      pageCounter <- pageCounter dec
      "pageCounter = pageCounter" echo
      tabView closeFinish: page confirm: true
      true
    ]
    overview
  ]
  
  window content: mainBox do;
    present
]


app run: args
"""

fun main(args: Array<String>) {
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/JSON/lexer.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/GTK/AdwDela/main.niva")
//    val args = arrayOf("test", "/home/gavr/Documents/Projects/bazar/Examples/tests/a.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/experiments/niva.niva")
//    val args = arrayOf("test", "/home/gavr/Documents/Projects/bazar/Examples/tests/main.niva")
    if (help(args)) return

//    val ggg = "file:///home/gavr/Documents/Projects/bazar/Examples/GTK/AdwDela/main.niva"
//    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/experiments/niva.niva"
//
////    LS().onCompletion(qqq, 11, 0)
//    val ls = LS()
//    val resolver = ls.resolveAll(qqq)
//
//    val fakeFileSource = newText
//
//    ls.resolveAllWithChangedFile(
//        qqq,
//        fakeFileSource
//    )



    run(args)
}

// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun getPathToMainOrSingleFile(args: Array<String>): String =
    if (args.count() >= 2) {
        // niva run/test/build "sas.niva"
        val fileNameArg = args[1]
        if (File(fileNameArg).exists()) {
            fileNameArg
        } else {
            createFakeToken().compileError("File $fileNameArg doesn't exist")
        }
    } else if (args.count() == 1 && args[0].contains(".")) {
        // Single arg "niva sas.niva"
        args[0]
    } else if (args.count() == 0) {
        File("examples/Main/main.niva").absolutePath
    }


    else {
        // niva run\test\build...
        val mainNiva = "main.niva"
        val mainScala = "main.scala"

        if (File(mainNiva).exists())
            mainNiva
        else if (File(mainScala).exists())
            mainScala
        else {
            println("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
            exitProcess(-1)
//                createFakeToken().compileError("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
        }
    }

fun run(args: Array<String>) {
    val argsSet = args.toSet()

//    readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(argsSet, args)
    val mainArg = am.mainArg()
    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg)

    if (mainArg == MainArgument.DAEMON) {
        daemon(pm, mainArg)
    }

    // resolve all files!
    val resolver = try {
        compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH, tests = mainArg == MainArgument.TEST, verbose = am.verbose)
    } catch (e: CompilerError) {
        println(e.message)
        exitProcess(-1)
    }
    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler = CompilerRunner(
        pm.pathToInfroProject,
        inlineRepl,
        resolver.compilationTarget,
        resolver.compilationMode,
        pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
        resolver
    )


    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD -> compiler.runCommand(dist = true, buildFatJar = true)
        MainArgument.DISRT -> compiler.runCommand(dist = true)
        MainArgument.RUN ->
            compiler.runCommand()

        MainArgument.TEST -> {
            compiler.runCommand(runTests = true)
        }

            MainArgument.SINGLE_FILE_PATH -> {
            compiler.runCommand(dist = am.compileOnly, singleFile = true)
        }

        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDEA -> {
            compiler.runCommand(dist = false, singleFile = true)
        }

        MainArgument.DAEMON -> {
            daemon(pm, mainArg)
        }

        MainArgument.LSP -> TODO()

    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

