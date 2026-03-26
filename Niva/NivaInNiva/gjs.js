imports.gi.versions["Gtk"] = "4.0";
const Gtk = imports.gi.Gtk;

// Create a new application
let app = new Gtk.Application({ application_id: "com.example.GtkApplication" });

// When the application is launched…
app.connect("activate", () => {
  // … create a new window …
  let win = new Gtk.ApplicationWindow({ application: app });
  // … with a button in it …
  let btn = new Gtk.Button({ label: "Hello, World!" });
  // … which closes the window when clicked
  btn.connect("clicked", () => {
    win.close();
  });
  win.set_child(btn);
  win.present();
});

// Run the application
app.run([]);
// import Gtk from "gi://Gtk?version=4.0";
// import GLib from "gi://GLib";

// // Initialize Gtk before you start calling anything from the import
// Gtk.init();

// // If you are not using GtkApplication which has its own mainloop
// // you must create it yourself, see gtk-application.js example
// let loop = GLib.MainLoop.new(null, false);

// // Construct a window
// let win = new Gtk.Window({
//   title: "A default title",
//   default_width: 300,
//   default_height: 250,
// });

// // Object properties can also be set or changed after construction, unless they
// // are marked construct-only.
// win.title = "Hello World!";

// // This is a callback function
// function onCloseRequest() {
//   log("close-request emitted");
//   loop.quit();
// }

// // When the window is given the "close-request" signal (this is given by the
// // window manager, usually by the "close" option, or on the titlebar), we ask
// // it to call the onCloseRequest() function as defined above.
// win.connect("close-request", onCloseRequest);

// // Create a button to close the window
// let button = new Gtk.Button({
//   label: "Close the Window",
//   // An example of how constants are mapped:
//   //     'Gtk' and 'Align' are taken from the GtkAlign enum,
//   //     'CENTER' from the constant GTK_ALIGN_CENTER
//   valign: Gtk.Align.CENTER,
//   halign: Gtk.Align.CENTER,
// });

// // Connect to the 'clicked' signal, using another way to call an arrow function
// button.connect("clicked", () => win.close());

// // Add the button to the window
// win.set_child(button);

// // Show the window
// win.present();

// // Control will end here and wait for an event to occur
// // (like a key press or mouse event)
// // The main loop will run until loop.quit is called.
// loop.run();

// log("The main loop has completed.");
