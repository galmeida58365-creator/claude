// Troca o ícone e os dados de versão do executável. Uso: node editar_exe.js programa.exe icone.ico 1.3.0
const fs = require("fs");
const ResEdit = require("resedit");

const [exe, ico, versao] = process.argv.slice(2);
const pe = ResEdit.NtExecutable.from(fs.readFileSync(exe), { ignoreCert: true });
const res = ResEdit.NtExecutableResource.from(pe);

const icone = ResEdit.Data.IconFile.from(fs.readFileSync(ico));
const grupo = ResEdit.Resource.IconGroupEntry.fromEntries(res.entries)[0];
ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, grupo.id, grupo.lang, icone.icons.map((i) => i.data));

const info = ResEdit.Resource.VersionInfo.fromEntries(res.entries)[0];
const [a, b, c] = versao.split(".").map(Number);
info.setFileVersion(a, b, c, 0);
info.setProductVersion(a, b, c, 0);
for (const lang of info.getAllLanguagesForStringValues()) {
  info.setStringValues(lang, {
    ProductName: "Caixa do Mês",
    FileDescription: "Caixa do Mês",
    CompanyName: "Caixa do Mês",
    InternalName: "CaixaDoMes",
    OriginalFilename: "CaixaDoMes.exe",
    LegalCopyright: "",
    FileVersion: versao,
    ProductVersion: versao,
  });
}
info.outputToResourceEntries(res.entries);
res.outputResource(pe);
fs.writeFileSync(exe, Buffer.from(pe.generate()));
console.log("Ícone e versão aplicados em", exe);
