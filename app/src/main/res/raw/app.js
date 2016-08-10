function start(){
    var socket = new WebSocket("ws://"+location.hostname+":8081/")
    var log = document.getElementById("values")
    socket.onmessage = function (event) {
      log.innerText = event.data
    }
}
setTimeout(start, 0);

