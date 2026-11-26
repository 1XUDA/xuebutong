import fs from 'node:fs/promises';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';
const book=Workbook.create();
const headers=['题型','题目','选项A','选项B','选项C','选项D','正确答案','解析'];
const data=book.worksheets.add('题库');
const examples=book.worksheets.add('填写示例');
for(const sheet of [data,examples]){
  sheet.getRange('A1:H1').values=[headers];
  sheet.getRange('A1:H1').format.fill='#BC292C';
  sheet.getRange('A1:H1').format.font.color='#FFFFFF';
  sheet.getRange('A1:H1').format.font.bold=true;
  sheet.getRange('A:A').format.columnWidth=14;
  sheet.getRange('B:B').format.columnWidth=36;
  sheet.getRange('C:F').format.columnWidth=16;
  sheet.getRange('G:G').format.columnWidth=16;
  sheet.getRange('H:H').format.columnWidth=36;
  sheet.getRange('A1:H1').format.rowHeight=25;
  sheet.freezePanes.freezeRows(1);
}
examples.getRange('A2:H5').values=[
 ['单选题','地球围绕什么运行？','月球','太阳','火星','木星','B','地球围绕太阳运行。'],
 ['多选题','下列哪些是哺乳动物？','鲸鱼','青蛙','蝙蝠','鳄鱼','AC','鲸鱼和蝙蝠都是哺乳动物。'],
 ['判断题','地球是太阳系的行星。','','','','','正确',''],
 ['简答题','水的化学式是什么？','','','','','H₂O','']
];
examples.getRange('A7:H7').values=[['此页仅供参考，不会导入。请在“题库”表按相同表头填写。','','','','','','','']];
await fs.mkdir('android-app/src/main/assets/bank',{recursive:true});
await (await SpreadsheetFile.exportXlsx(book)).save('android-app/src/main/assets/bank/template.xlsx');
for(const name of ['题库','填写示例']){const png=await book.render({sheetName:name,range:'A1:H7',scale:1});await fs.writeFile(`../${name}-template-preview.png`,new Uint8Array(await png.arrayBuffer()));}
console.log((await book.inspect({kind:'sheet',include:'id,name'})).ndjson);
