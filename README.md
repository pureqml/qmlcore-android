# QMLCore Android Native Port

The aim of this project — to provide really thin runtime system, potentially cross-platform.

## FemtoDOM Design

FemtoDOM contains absolute minimum of API, allowing you to run PureQML natively on any platform we'd port it. 

fd — global namespace object
fd.context - instance of fd.Element, root context, with width/height equals to some device-dependent units (not necessary pixels)

```javascript

class fd.Element {
  contructor (optional parent) { /* creates and adds this element to parent */ }
  append(element) { /*adds child*/ }
  remove() { /*remove element from current parent */ }
  style(name, value) { /*set specific style to <value>, styles are write-only, no way to get them back */ }

  //geometry (r/w)
  property left, top, width, height

//event interface
  on(string, callback) //returns fd.EventConnection
  emit(string, arguments)
}

class fd.Image extends fd.Element {
  load(source) { /*loads image, signal loaded event */ }
}

class fd.Text extends fd.Element {
  property text;
  measure(text) { /* return fd.TextMetrics */ }
}

class fd.EventConnection {
  property callback; //callback used for event
  cancel() { callback = null; } //sugar for unsubscribing
}

class fd.Timer {
  static setTimeout(callback, ms) { }
  static setInterval(callback, ms) { }

  cancel() { /*cancels current timer*/ }
}

fd.fetch - http request, the same options/arguments. 
```
