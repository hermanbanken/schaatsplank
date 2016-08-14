var j = 0;
var t = 0;
var distances = [0.30] // change to 2m later
var times = [2000]
var speed = 0;
var position = false;
//var interval = setInterval(step, 100)

function step(){
    var evt = mock[j++];
    j = j % mock.length;
    if(!evt) return clearInterval(interval);
    t += 100;

    // At start we don't know our position
    if(position === false) {
        position = evt.speed > 0 ? -1 : 1;
    }

    if(evt.where == "LEFT" || evt.where == "RIGHT") {
        distances.unshift(Math.abs(evt.distance));
        times.unshift(t);
        position = evt.where == "LEFT" ? 1 : -1;
    }

    // Adjust
    speed = evt.speed / Math.abs(distances[0]) / times[0] * 1000;
//    console.log(speed);
}



var last, z = 0;
function anim(time){
    if((z++) % 3 == 0) {
        if (!last) last = time;
        var dt = (time - last) / 1000;
        last = time;

        console.log(position, position + speed * dt, speed);
        position = position + speed * dt;
    //    var p = Math.sin(position * Math.PI / 2)
        move(position)
    }
    window.requestAnimationFrame(anim);
}
//window.requestAnimationFrame(anim);

function move(position){
    document.getElementById("circle").style.left = (position*100)+"%";
}

//var start = document.getElementsByTagName("button")[0];
//start.onclick = function(){
//    socket.send(JSON.stringify({
//        "name": "Anoniem",
//        "email": "",
//        "distance": 500
//    }))
//}

//var message = document.getElementById("message");
//message.style.display = 'none';
//function showMessage(text) {
//    start.style.display = 'none';
//    message.style.display = 'block';
//    message.innerText = text;
//    setTimeout(function(){
//        message.style.display = 'none';
//    }, 1000);
//}

Vue.filter('time', function (value) {
  var minutes = Math.floor(value / 60)
  var seconds = Math.floor(value % 60)
  var millis = value % 1
  if(minutes) {
    return minutes + ":" + seconds.toFixed(0) + "." + millis.toFixed(2).substr(2)
  } else {
    return seconds.toFixed(0) + "." + millis.toFixed(2).substr(2)
  }
})

Vue.filter('pts', function (value) {
  if(!value) { return "-" }
  var seconds = Math.floor(value)
  var millis = value % 1
  return seconds.toFixed(0) + "." + millis.toFixed(3).substr(2)
})

Vue.component('nos', Vue.extend({
  template: "<h1 class='fill nos' style='margin: 0 0 2vw'><div class='diagl'></div><div class='th darker'><slot></slot></div><div class='diagr'></div></h1>"
}))

Vue.component('knop', Vue.extend({
  template: "<nos><button v-on:click=\"$dispatch('click')\"><slot></slot></button></nos>"
}))

new Vue({
  el: 'body',
  data: {
    match: {
      time: 0,
      distance: 0,
    },
    config: false,
    socket: null,
    mode: 0,
    distances: [
      { value: 500, name: 'infomarkt', shown: true },
      { value: 1000, name: 'sportmarkt', shown: false },
    ],
    skaters: [
      { name: "Herman Banken" },
      { name: "Vincent van der Wal" },
      { name: "Dummy" },
    ],
    results: [
      { name: "Herman Banken", distance: 500, time: 41.99 },
      { name: "Vincent van der Wal", distance: 1000, time: 84.99 },
    ],
    message: null,
    hasMessage: false,
  },
  methods: {
    send: function (data) {
        this.socket.send(JSON.stringify(data));
    },
    receive: function (data) {
      console.log("data", event.data);
      if(data.event == 'message') {
        return this.showMessage(data.message);
      }
    },
    connected: function (event) {
        console.log("connect");
    },
    disconnected: function (event) {
        console.log("disconnect", event.code, event.reason, event.wasClean);
    },
    // Sorts
    bestRanking: function (a, b) {
      if (a.distances != b.distances) return b.distances - a.distances;
      return a.pts - b.pts;
    },
    // Actions
    showMessage: function (message) {
      this.message = message;
      this.hasMessage = true;
      setTimeout(function(){
        if(this.message == message) { this.hasMessage = false }
      }.bind(this), 1500);
    },
    menu: function(){
      if(this.mode == 0) this.mode = -1;
    },
    start: function(distance){
      if(this.mode == 0) this.mode = 1;
      this.send({ event: 'start', distance: distance })
    },
    home: function(){
      if(this.mode == 1 && confirm("Sure?")) {

      }
      this.mode = 0;
    },
  },
  computed: {
    // Rankings
    rankings: function () {
      var self = this;
      return self.skaters.map(function(sk){
        var index = {};
        var pts = 0;
        var distances = 0;
        self.results.forEach(function(r){
          if(r.name == sk.name) {
            index[r.distance] = r;
            pts += r.time / r.distance * 500;
            distances += 1;
          }
        });
        var results = self.distances.map(function(d){
          return {
            distance: d.value,
            result: index[d.value],
            shown: d.shown,
          }
        });
        return {
          name: sk.name,
          results: results,
          pts: pts,
          distances: distances,
        }
      })
    }
  },
  created: function (){
    if(!location.hostname) { return; }
    this.socket = new WebSocket("ws://"+location.hostname+":8081/")
    this.socket.addEventListener('message', function(event){
      try {
        this.receive(JSON.parse(event.data));
      } catch(e) {
        this.receive(event.data);
      }
    }.bind(this));
    this.socket.addEventListener('open', this.connected);
    this.socket.addEventListener('close', this.disconnected);
  }
})