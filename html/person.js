function Person(svg_selector) {
  this.svg = Snap(svg_selector);
  this.parts = ["body", "arms", "head"]
  this.types = ["deep", "standing"];
  this.type = 0;
  this.offsets = [{ transform: "translate(-25,55)" }, { transform: "translate(0,0)" }]

  this.colorMorph = new ColorMorph(0xff0000, 0x00ff00)
  this.morphs = this.parts.map(function(part) {
    return new PathMorph(
      this.svg.select("#standing_"+part).attr('d'),
      this.svg.select("#deep_"+part).attr('d')
    );
  }.bind(this));

  this.svg.select("#final").attr({"fill": '#4360AD', "stroke": '#4360AD'});
}

Person.prototype.show = function(factor, angle) {
  var a = Math.max(Math.min(90, angle), 30) / 45;
  var f = Math.max(0.5, Math.min(1, factor)) * 2 - 1;
  var x = -25 * a, y = 55 * a;
  this.svg.select("#body").animate({ d: this.morphs[0].to(a) }, 200);
  this.svg.select("#arms").animate({ d: this.morphs[1].to(a) }, 200);
  this.svg.select("#head").animate({ d: this.morphs[2].to(a) }, 200);
  var color = this.colorMorph.to(f);
  this.svg.select("#final").animate({ transform: "translate("+x+","+y+")", fill: color, stroke: color }, 200);
  this.svg.select("text").node.innerHTML = (angle.toFixed(0)) + "°";
  this.svg.select("text").attr({ fill: color })
}

Person.prototype.move = function(){
  this.pos = (this.pos + 1) % this.positions.length;
  var t = this.positions[this.pos] * 2 - 1;
  t += 0.2
  var x = -25 * t, y = 55 * t;
  this.svg.select("#body").animate({ d: this.morphs[0].to(t) }, 200);
  this.svg.select("#arms").animate({ d: this.morphs[1].to(t) }, 200);
  this.svg.select("#head").animate({ d: this.morphs[2].to(t) }, 200);
  var color = this.colorMorph.to(t);
  this.svg.select("#final").animate({ transform: "translate("+x+","+y+")", fill: color, stroke: color }, 200);
}

function prefix(string, length, pre) {
  var result = "" + string;
  while(result.length < length) {
    result += "0" + string
  }
  return result
}

function ColorMorph(a, b){
  var as = [a >> 16, (a >> 8) & 0xff, (a >> 0) & 0xff]
  var bs = [b >> 16, (b >> 8) & 0xff, (b >> 0) & 0xff]
  this.to = function(t) {
    return rgbToHex(
      prefix(as[0] + t * (bs[0] - as[0]) >> 0, 2),
      prefix(as[1] + t * (bs[1] - as[1]) >> 0, 2),
      prefix(as[2] + t * (bs[2] - as[2]) >> 0, 2)
    )
  }
}

function PathMorph(a, b) {
  var toStringF = getPath(Snap.path.toCubic(a));
  var pathA = path2array(Snap.path.toCubic(a)),
      pathB = path2array(Snap.path.toCubic(b)),
      p2s = /,?([a-z]),?/gi;

  function toString() {
    return this.join(",").replace(p2s, "$1");
  }

  function path2array(path) {
    var out = [];
    for (var i = 0, ii = path.length; i < ii; i++) {
        for (var j = 1, jj = path[i].length; j < jj; j++) {
            out.push(path[i][j]);
        }
    }
    return out;
  }

  function to(t) {
    var res = [];
    for (var j = 0, end = pathA.length; j < end; j++) {
      res[j] = pathA[j] + (pathB[j] - pathA[j]) * t;
    }
    res.toString = function() { return toStringF(this) };
    return res;
  }

  /*
   * Use with original path, before this is flattened
   * to generate a toString method specific for this path type
   */
  function getPath(path) {
    var k = 0, i, ii, j, jj, out, a, b = [];
    for (i = 0, ii = path.length; i < ii; i++) {
      out = "[";
      a = ['"' + path[i][0] + '"'];
      for (j = 1, jj = path[i].length; j < jj; j++) {
        a[j] = "val[" + (k++) + "]";
      }
      out += a + "]";
      b[i] = out;
    }
    return Function("val", "return Snap.path.toString.call([" + b + "])");
  }

  this.to = to;
}

function componentToHex(c) {
    var hex = Math.min(255, Math.max(0, c)).toString(16);
    return hex.length == 1 ? "0" + hex : hex;
}

function rgbToHex(r, g, b) {
    return "#" + componentToHex(r >> 0) + componentToHex(g >> 0) + componentToHex(b >> 0);
}

