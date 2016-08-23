Vue.filter('time', function (value) {
  if(!value || isNaN(value)) {
    return "-"
  }
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
Vue.component('btn', Vue.extend({
  template: "<div class='fill btn' v-on:click=\"$dispatch('click')\"><div class='diagl'></div><div class='th darker'><slot></slot></div><div class='diagr'></div></div>"
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
      gravityfactor: 0,
      speed: 0,
      acc: 0,
    },
    name: "",
    config: false,
    socket: null,
    person: new Person("#person"),
    mode: 0,
    distances: [
      { value: 5, name: 'test', shown: true },
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
    offline: false,
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
      if(data.angle) {
        this.person.show(data.factor, data.angle);
      }
      if(data.speed) {
        this.match.speed = data.speed
      }
      if(data.extra && data.extra.gravityfactor) {
        this.match.gravityfactor = data.extra.gravityfactor
      }
      if(data.extra && data.extra.acc) {
        this.match.acc = data.extra.acc
      }
      if(data.event == 'start') {
        this.mode = 1;
        this.name = "";
        this.match = { time: 0, total_distance: data.distance, distance: 0, done: false, gravityfactor: 0, speed: 0, acc: 0 };
        var p = film.play();
        if (p && (typeof Promise !== 'undefined') && (p instanceof Promise)) {
            p.catch(function (e) {
                console.log("Caught pending play exception - continuing ("+e+"})");
            })
        }
      }
      if(data.event == 'done') {
        film.currentTime = 0;
        film.pause();
        this.match.done = true;
        this.mode = -1;
      }
      if(data.event == 'message') {
        return this.showMessage(data.message);
      }
      if(data.event == 'ping') {
        this.ping = new Date();
        clearTimeout(this.pingTimeout);
        this.pingTimeout = setTimeout(function(){
          console.log("maybe gone");
          window.t = this;
        }, 4000)
        window.$vm = this;
      }
      if(data.event == 'remove') {
        this.skaters.splice(data.number, 1);
      }
      if(data.event == 'clear') {
        this.skaters = [];
        this.results = [];
      }
      if(data.result) {
        this.results.forEach(function(r, i) {
         if(data.number && r.number == data.number) {
          this.results.$set(i, data)
         }
        }.bind(this))
        this.match.number = Math.max(this.match.number || 0, data.number);
        this.results.push(data);
      }
    },
    connected: function (event) {
      this.offline = false
    },
    disconnected: function (event) {
      this.offline = true;
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
    start: function(distance, name){
      var packet = { event: 'start', distance: distance }
      if(name) { packet.name = name }
      this.send(packet)
      this.match.total_distance = distance;
      this.mode = 1;
    },
    home: function(){
      if(this.mode == 1 && confirm("Sure?")) {
        this.send({ event: 'stop' });
      }
      this.mode = 0;
    },
    clear_ranking: function(){
      this.send({ event: "clear_ranking" })
    },
    save: function (skater) {
      skater.results.forEach(function(data){
        data.result && this.send({
          event: "name",
          name: skater.name,
          number: data.result.number,
        })
      }.bind(this))
    },

    reconnect: function() {
      var url = "ws://" + (location.hostname || "127.0.0.1") + ":8081/"
      var message = function(event){
        try {
          this.receive(JSON.parse(event.data));
        } catch(e) {
          this.receive(event.data);
        }
      }.bind(this);

      var retry = function(){
        setTimeout(this.reconnect.bind(this), 3000);
      }.bind(this)

      var socket = this.socket = new WebSocket(url)
      socket.addEventListener('message', message);
      socket.addEventListener('open', this.connected);
      socket.addEventListener('close', this.disconnected);
      socket.addEventListener('close', retry);
    },
  },
  computed: {
    // Skaters
    skaters: function () {
      var skaters = {}
      this.results.forEach(function(result) {
        result.time = result.time || result.result
        if(result.name) {
          skaters[result.name] = skaters[result.name] || { name: result.name, results: [] }
          skaters[result.name].results.push(result)
        } else {
          skaters[result.number] = { name: "anonymous "+result.number, results: [result] }
        }
      });
      return Object.keys(skaters).map(function(key){ return skaters[key] });
    },
    // Rankings
    rankings: function () {
      var self = this;
      return self.skaters.map(function(sk){
        var index = {};
        sk.results.forEach(function(r){
          if(!index[r.distance]) {
            index[r.distance] = r;
          } else if(index[r.distance].time > r.time) {
            index[r.distance] = r;
          }
        });
        var results = self.distances.map(function(d){
          return {
            distance: d.value,
            original: index[d.value],
            shown: d.shown,
          }
        });
        var withResults = results.filter(function(r){ return r.original })
        var pts = withResults.reduce(function(p, r){ return p + (r.original.time || r.original.result) / r.original.distance * 500 }, 0)
        return {
          name: sk.name,
          results: results,
          pts: pts,
          distances: withResults.length,
        }
      })
    },
    latest: function() {
      for(var i = 0; i < this.skaters.length; i++) {
        for(var j = 0; j < this.skaters[i].results.length; j++) {
          if(this.skaters[i].results[j].number == this.match.number) {
            return this.skaters[i];
          }
        }
      }
      return false;
    },
  },
  created: function (){
    this.reconnect()
    window.person = this.person
  }
});
