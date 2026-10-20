// Batch SVG -> PNG renderer (resvg). Usage: node render.js jobs.json
// jobs.json = [{ "svg": "path.svg", "png": "out.png", "width": 192 }, ...]
// Needs @resvg/resvg-js on NODE_PATH:  npm install --prefix <dir> @resvg/resvg-js
//                                      NODE_PATH=<dir>/node_modules node render.js jobs.json
const fs = require('fs');
const { Resvg } = require('@resvg/resvg-js');

const jobs = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
for (const job of jobs) {
  const svg = fs.readFileSync(job.svg, 'utf8');
  const png = new Resvg(svg, {
    fitTo: { mode: 'width', value: job.width },
    background: 'rgba(0,0,0,0)',
    font: { loadSystemFonts: true },
  }).render().asPng();
  fs.writeFileSync(job.png, png);
}
console.log(`rendered ${jobs.length}`);
