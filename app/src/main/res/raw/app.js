Vue.filter('time', function (value) {
  var minutes = Math.floor(value / 60)
  var seconds = Math.floor(value % 60)
  var millis = value % 1
  if(minutes) {
    return minutes + ":" + zeros(seconds.toFixed(0)) + "." + zeros(millis.toFixed(2)).substr(2)
  } else {
    return seconds.toFixed(0) + "." + zeros(millis.toFixed(2)).substr(2)
  }
})

function zeros(v) {
    if(v.length == 0) return "00";
    if(v.length == 1) return "0"+v;
    return v;
}

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

var film = document.getElementById("film");
var loopStart = 10 // 24 + 25/60;
var loopEnd = 12 // 42 + 21/60;
var loopDuration = loopEnd - loopStart;
function playing(){
  setTimeout(function(){
    console.log(film.currentTime, loopEnd, loopStart);
    if(film.currentTime > loopEnd - 5) {
      film.currentTime -= loopDuration;
      playing();
    }
  }, (loopEnd - film.currentTime - 5) * 1000);
}
function stopped(){
  if(film.currentTime > loopEnd) {
    film.currentTime -= loopDuration;
    film.play();
  }
}

new Vue({
  el: 'body',
  data: {
    match: {
      time: 0,
      distance: 0,
    },
    name: "",
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
      if(data.time) {
        this.match.time = data.time;
      }
      if(data.distance) {
        this.match.distance = data.distance;
      }
      if(data.event == 'start') {
        this.name = "";
        this.match.time = 0;
        this.match.distance = 0;
        var p = film.play();
        if (p && (typeof Promise !== 'undefined') && (p instanceof Promise)) {
            p.catch((e) => {
                console.log("Caught pending play exception - continuing ("+e+"})");
            });
        }
      }
      if(data.event == 'done') {
        film.currentTime = 0;
        this.mode = 0;
      }
      if(data.event == 'message') {
        return this.showMessage(data.message);
      }
      if(data.event == 'clear') {
        this.skaters = [];
        this.results = [];
      }
      if(data.result) {
        if(!data.time) data.time = data.result;
        var existing = this.skaters.find(function(s){ s.name == data.name });
        if(!existing) {
          this.skaters.push({ name: data.name });
        }
        this.results.push(data);
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
      this.match.total_distance = distance;
    },
    home: function(){
      if(this.mode == 1 && confirm("Sure?")) {
        this.send({ event: 'stop' });
      }
      this.mode = 0;
    },
    clear_ranking: function(){
      this.send({ event: "clear_ranking" })
    }
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
  watch: {
    "name": function(name) {
      this.send({
        event: "name",
        name: name
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