var socket = new WebSocket("ws://"+location.hostname+":8081/stream", "protocolOne")
socket.onmessage = function (event) {
  console.log("data", event.data);
}