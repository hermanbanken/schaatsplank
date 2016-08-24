function Video(selector){
  var vid = document.querySelector(selector);
  vid.querySelector("source")
  vid.preload = true;
  this.vid = vid;

  function currentSource(){
    return []
      .slice.call(vid.querySelectorAll('source'),0)
      .filter(s => s.src == vid.currentSrc)[0]
  }

  function loopRange(){
    var s = currentSource()
    return [
      s && s.dataset && parseFloat(s.dataset.loopstart) || 0,
      s && s.dataset && parseFloat(s.dataset.loopend) || vid.duration
    ]
  }

  function loadedmetadata(e){
    console.log("metadata loaded", vid.currentTime, vid.duration, e);
//    setInterval(function() {
//      vid.currentTime = 0;
//    }, 1000);
  }

  var pendingSeek = null
  var range = null
  function playing(e) {
    console.log("playing", e.timeStamp, vid.currentTime);
    range = loopRange()
    console.log("Scheduled seek", range[1] - vid.currentTime, pendingSeek);
    clearTimeout(pendingSeek);
    pendingSeek = setTimeout(jump.bind(null, range[0]), (range[1] - vid.currentTime) * 1000)
  }

  function pause(e) {
    console.log("pause", e.timeStamp, vid.currentTime);
  }
  function waiting(e) {
    console.log("waiting", e.timeStamp, vid.currentTime);
  }

  function timeupdate(e) {
    console.log("timeupdate", e.timeStamp, vid.currentTime);
    if(vid.currentTime > range[1]) {
      console.log("Immediate seek");
      jump(range[0])
    }
  }

  vid.addEventListener("loadedmetadata", loadedmetadata)
  vid.addEventListener("playing", playing)
  vid.addEventListener("pause", pause)
  vid.addEventListener("waiting", waiting)
  vid.addEventListener("timeupdate", timeupdate)

  function play() {
    var p = vid.play();
    if (p && (typeof Promise !== 'undefined') && (p instanceof Promise)) {
      p.catch(function (e) {
        console.log("Caught pending play exception - continuing ("+e+"})");
      })
    }
    return p;
  }

  this.play = play;
  this.playbackRate = function(val){
    console.log(val); // && window.navigator.platform.indexOf("Win") == 0
    vid.playbackRate = val
  }
  function jump(val, maybePlay){
    window.vid = vid;
    vid.currentTime = val;
    if(typeof maybePlay == 'undefined' || maybePlay) {
      play()
    }
  }
  this.jump = jump;
  this.pause = function(val){
    vid.pause()
  }
}

//var loopStart = 10 // 24 + 25/60;
//var loopEnd = 12 // 42 + 21/60;
//var loopDuration = loopEnd - loopStart;
//function playing(){
//  setTimeout(function(){
//    console.log(film.currentTime, loopEnd, loopStart);
//    if(film.currentTime > loopEnd - 5) {
//      film.currentTime -= loopDuration;
//      playing();
//    }
//  }, (loopEnd - film.currentTime - 5) * 1000);
//}
//function stopped(){
//  if(film.currentTime > loopEnd) {
//    film.currentTime -= loopDuration;
//    film.play();
//  }
//}