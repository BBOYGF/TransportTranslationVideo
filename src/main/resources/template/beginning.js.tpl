import * as mo from 'movy'
// movy .\videos\heginning.js
// 本文件由 TransportTranslationVideo 自动生成，图片和标题会被自动替换
let image = mo.addImage("{{IMAGE}}", {x: 0, y: 0, scale: {{SCALE}}, t: 0, bloomEnabled: false});
image.show({t: 0, duration: 1})
image.show({t: 1, duration: -1})
image.bloomEnabled = true
var color = "#ffeb00";
const textTitle = mo.addText("{{TEXT1}}", {
    x: 0,
    y: 1,
    z: 3,
    fontSize: 0.6,
    font: 'gdh',
    color: color,
    bloomEnabled: false
});
textTitle.show({t: 0, duration: 1})
textTitle.show({t: 1, duration: -1})
const textTitle2 = mo.addText("{{TEXT2}}", {
    x: 0,
    y: 0,
    z: 3,
    fontSize: 0.6,
    font: 'gdh',
    color: color,
    bloomEnabled: false
});
textTitle2.show({t: 0, duration: 1})
textTitle2.show({t: 1, duration: -1})

let circle1 = mo.addCircle({x: 0, y: 10, scale: 0.1, t: 1});
circle1.moveTo({x: 0, y: 0, t: 1, duration: 0.5, ease: "bounce.out"})

mo.addGlitch({t: 1.5})
let preX, preY, preZ = 0;
for (let i = 0; i < 50; i = i + 0.12) {
    if (i < 5) {
        let line = mo.addLine({from: [preX, preY], to: [i, Math.sin(i)], lineWidth: 0.05, t: 1.5});
        line.fadeIn({
            t: "<0.01",
        });
        line.show({t: "<0.01", duration: 1})
    } else {
        let line = mo.addLine({from: [preX, preY, preZ], to: [i, Math.sin(i), Math.cos(i)], lineWidth: 0.05, t: 1.5});
        line.fadeIn({
            t: "<0.005",
        });
    }
    preX = i;
    preY = Math.sin(i);
    preZ = Math.cos(i);
}

mo.cameraMoveTo({x: 3.6, y: 0, z: 0, t: 2.2, duration: 1})
mo.cameraMoveTo({ry: -0.5 * Math.PI, z: -1.1, t: 2.2, duration: 1})
mo.cameraMoveTo({x: 25, y: 0, z: 0, t: 2.5, duration: 1, ease: "circ.inOut"})
let circle = mo.addCircle({x: 60, y: 0, z: 0, ry: 0.5 * Math.PI, t: 0});
circle.moveTo({x: 25.5, y: 0, z: 0, t: 2.5, duration: 2, ease: "power4.inOut"})
mo.run();
