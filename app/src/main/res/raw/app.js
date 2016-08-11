function start(){
    var socket = new WebSocket("ws://"+location.hostname+":8081/")
    var log = document.getElementById("values")
    socket.onmessage = function (event) {
        var vals = JSON.parse(event.data)
        console.log(vals)
        if(!series.length) {
            series = vals.map(function(_, i){
                var serie = new TimeSeries()
                var color = [0,1,2].map(function(v){ return v == i ? 255 : 0 }).toString()
                chart.addTimeSeries(serie, { strokeStyle: "rgba("+color+",1)", fillStyle: "rgba("+color+",0.2)", lineWidth: 1 })
                return serie
            });
            chart.streamTo(document.getElementById("chart"), 250);
        }
        for(var i = 0; i < series.length; i++) {
            series[i].append(new Date().getTime(), vals[i]);
        }
    }
}
setTimeout(start, 0);

var series = []
var chart = new SmoothieChart({
    grid: { strokeStyle:'rgb(125, 0, 0)', fillStyle:'rgb(60, 0, 0)',
            lineWidth: 1, millisPerLine: 250, verticalSections: 6, },
    labels: { fillStyle:'rgb(60, 0, 0)' }
});