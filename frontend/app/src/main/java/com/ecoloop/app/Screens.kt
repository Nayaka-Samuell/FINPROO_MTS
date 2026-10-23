package com.ecoloop.app

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.delay

@Composable fun EcoField(label:String,value:String,secret:Boolean=false,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),singleLine=true,visualTransformation=if(secret)androidx.compose.ui.text.input.PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None,shape=RoundedCornerShape(16.dp))}
@Composable fun DistrictPicker(value:String,onPick:(String)->Unit){var expanded by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={expanded=true},Modifier.fillMaxWidth()){Text(value)};DropdownMenu(expanded,{expanded=false}){districts.forEach{DropdownMenuItem(text={Text(it)},onClick={onPick(it);expanded=false})}}}}
@Composable fun EcoPrimaryButton(label:String,loading:Boolean,onClick:()->Unit){Button(onClick=onClick,enabled=!loading,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)){if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)else Text(label)}}
@Composable fun ErrorText(vm:EcoViewModel){val error by vm.error.collectAsState();error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))}}
@Composable fun EcoCategoryChip(category:CategoryDto,selected:Boolean,onClick:()->Unit){FilterChip(selected=selected,onClick=onClick,label={Text(category.name)},shape=RoundedCornerShape(22.dp))}
@Composable fun EcoStatusBadge(status:String){val (label,bg,fg)=when(status){"REQUESTED"->Triple("Menunggu persetujuan",Color(0xFFFFE8C2),Color(0xFF8A4B08));"APPROVED"->Triple("Siap diambil",Color(0xFFDCEFE3),Color(0xFF176347));"REJECTED"->Triple("Ditolak",Color(0xFFFFE1E1),Color(0xFF9B2525));"CANCELLED"->Triple("Dibatalkan",Color(0xFFE8E9E8),Color(0xFF4B514E));"COMPLETED"->Triple("Selesai",Color(0xFFD7F0D8),Color(0xFF205C2A));else->Triple(status,Color.LightGray,Color.DarkGray)};Surface(color=bg,shape=RoundedCornerShape(22.dp)){Text(label,color=fg,fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))}}
@Composable fun EcoEmptyState(title:String,actionText:String="Hapus filter",onAction:()->Unit){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,fontWeight=FontWeight.SemiBold);Text("Coba ubah kata kunci atau pilihan filter.",color=Color.Gray,modifier=Modifier.padding(6.dp));TextButton(onClick=onAction){Text(actionText)}}}
@Composable fun EcoMaterialCard(item:ListingDto,onClick:()->Unit){Card(onClick=onClick,Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column{AsyncImage(model=item.displayPhoto,contentDescription=item.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(140.dp));Column(Modifier.padding(11.dp)){Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(20.dp)){Text(item.categoryName,fontSize=11.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))};Text(item.title,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=2,minLines=2,modifier=Modifier.padding(top=7.dp));Text("%.2f kg tersedia".format(item.availableKg),fontSize=13.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(item.district,fontSize=12.sp,color=Color.Gray)}}}}
@Composable fun HomeScreen(vm:EcoViewModel,onSelect:(ListingDto)->Unit,onAdd:()->Unit){
 val listingItems by vm.list.collectAsState();val cats by vm.categories.collectAsState();val loading by vm.loading.collectAsState();val user by vm.user.collectAsState()
 var query by remember{mutableStateOf("")};var selectedCategory by remember{mutableStateOf<String?>(null)};var area by remember{mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)){
  Text("Halo, ${user?.name?.substringBefore(' ')?:"Teman"}",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp));Text("DKI Jakarta",color=MaterialTheme.colorScheme.primary)
  Card(Modifier.fillMaxWidth().padding(top=14.dp),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Bahan sisa, peluang baru.",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Temukan atau bagikan material untuk digunakan kembali.",fontSize=13.sp)};Button(onClick=onAdd){Text("Bagikan material")}}}
  OutlinedTextField(query,{query=it;vm.search(query,selectedCategory,area)},Modifier.fillMaxWidth().padding(top=16.dp),placeholder={Text("Cari material...")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query="";vm.search("",selectedCategory,area)}){Text("×")}},singleLine=true,shape=RoundedCornerShape(16.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=10.dp)){FilterChip(selected=selectedCategory==null,onClick={selectedCategory=null;vm.search(query,null,area)},label={Text("Semua")},modifier=Modifier.padding(end=6.dp));cats.forEach{c->EcoCategoryChip(c,selectedCategory==c.id){selectedCategory=if(selectedCategory==c.id)null else c.id;vm.search(query,selectedCategory,area)}}}
  Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(selected=area==null,onClick={area=null;vm.search(query,selectedCategory,null)},label={Text("Semua wilayah")});districts.forEach{d->FilterChip(selected=area==d,onClick={area=d;vm.search(query,selectedCategory,area)},label={Text(d)},modifier=Modifier.padding(start=6.dp))}}
  Text("Material untukmu",fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=8.dp))
  Crossfade(targetState=loading,label="catalog state"){busy->when{busy->CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(30.dp));listingItems.isEmpty()->EcoEmptyState("Belum ada material yang cocok",onAction={query="";selectedCategory=null;area=null;vm.search("",null,null)});else->LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.height(700.dp),contentPadding=PaddingValues(bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(listingItems,key={it.id}){row->EcoMaterialCard(row){onSelect(row)}}}}}
  ErrorText(vm)
 }
}
@Composable fun DetailScreen(initial:ListingDto,vm:EcoViewModel){
 var item by remember(initial.id){mutableStateOf(initial)};var show by remember{mutableStateOf(false)};var kg by remember{mutableStateOf("")}
 LaunchedEffect(initial.id){runCatching{item=vm.loadDetail(initial.id)}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)){
  AsyncImage(model=item.displayPhoto,contentDescription=item.title,modifier=Modifier.fillMaxWidth().height(260.dp),contentScale=ContentScale.Crop)
  Text(item.categoryName.uppercase(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp));Text(item.title,fontSize=26.sp,fontWeight=FontWeight.Bold);Text("${item.availableKg} kg tersedia dari ${item.totalKg} kg",color=MaterialTheme.colorScheme.primary);Text(item.district,Modifier.padding(top=8.dp));Text("Penyedia EcoLoop",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=16.dp));Text(item.description,modifier=Modifier.padding(vertical=10.dp));EcoPrimaryButton("Ajukan Reservasi",false){show=true};ErrorText(vm)
 }
 if(show)AlertDialog(onDismissRequest={show=false},title={Text("Ajukan reservasi")},text={Column{Text("Stok tersedia ${item.availableKg} kg");OutlinedTextField(kg,{kg=it},label={Text("Berat yang dibutuhkan (kg)")})}},confirmButton={TextButton(onClick={val n=kg.toDoubleOrNull();if(n!=null)vm.reserve(item,n){show=false}}){Text("Kirim permintaan")}},dismissButton={TextButton(onClick={show=false}){Text("Batal")}})
}
@Composable fun AddListingScreen(vm:EcoViewModel){val cats by vm.categories.collectAsState();var title by remember{mutableStateOf("")};var selected by remember{mutableStateOf<CategoryDto?>(null)};var kg by remember{mutableStateOf("")};var area by remember{mutableStateOf(districts[0])};var description by remember{mutableStateOf("")};var photo by remember{mutableStateOf("")};var expanded by remember{mutableStateOf(false)};val busy by vm.busy.collectAsState();Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Text("Bagikan material",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("Bantu material menemukan penggunaan berikutnya.",color=Color.Gray);EcoField("Judul material",title){title=it};Box{OutlinedButton(onClick={expanded=true},Modifier.fillMaxWidth()){Text(selected?.name?:"Pilih kategori")};DropdownMenu(expanded,{expanded=false}){cats.forEach{DropdownMenuItem(text={Text(it.name)},onClick={selected=it;expanded=false})}}};EcoField("Total berat (kg)",kg){kg=it};DistrictPicker(area){area=it};EcoField("Deskripsi",description){description=it};EcoField("URL foto (opsional)",photo){photo=it};EcoPrimaryButton("Terbitkan listing",busy){val n=kg.toDoubleOrNull();val c=selected;if(title.isNotBlank()&&n!=null&&n>0&&c!=null)vm.addListing(ListingCreateRequest(c.id,title,description,n,area,if(photo.isBlank())emptyList()else listOf(photo)))};ErrorText(vm)}}
@Composable fun ActivityScreen(vm:EcoViewModel){
 var tab by remember{mutableIntStateOf(0)};val incoming by vm.incoming.collectAsState();val outgoing by vm.outgoing.collectAsState();var qr by remember{mutableStateOf<Bitmap?>(null)};var countdown by remember{mutableIntStateOf(900)};var completeReservation by remember{mutableStateOf<ReservationDto?>(null)};var token by remember{mutableStateOf("")};var actual by remember{mutableStateOf("")};var success by remember{mutableStateOf(false)}
 LaunchedEffect(qr){while(qr!=null&&countdown>0){delay(1000);countdown--}}
 Column(Modifier.fillMaxSize().padding(18.dp)){
  Text("Aktivitas",fontSize=26.sp,fontWeight=FontWeight.Bold);TabRow(tab){listOf("Permintaan Masuk","Reservasi Saya").forEachIndexed{i,label->Tab(selected=tab==i,onClick={tab=i},text={Text(label)})}}
  val records=if(tab==0)incoming else outgoing
  if(records.isEmpty())EcoEmptyState("Belum ada aktivitas",actionText="Muat ulang",onAction=vm::refresh) else LazyColumn(contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   items(records,key={it.id}){r->Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp)){Text(r.listingTitle,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(tab==0)"Pemesan: ${r.receiverName}" else "Penyedia: ${r.supplierName}",color=Color.Gray);Text("${r.requestedKg} kg · ${r.listingDistrict}",modifier=Modifier.padding(vertical=6.dp));EcoStatusBadge(r.status)
    if(tab==0&&r.status=="REQUESTED")Row{TextButton(onClick={vm.approve(r.id,true)}){Text("Setujui")};TextButton(onClick={vm.approve(r.id,false)}){Text("Tolak")}}
    if(tab==0&&r.status=="APPROVED")Button(onClick={completeReservation=r},Modifier.fillMaxWidth().padding(top=8.dp)){Text("Selesaikan Serah Terima")}
    if(tab==1&&r.status=="APPROVED")Button(onClick={countdown=900;vm.handover(r.id){t->token=t.token;qr=makeQr(t.token)}},Modifier.fillMaxWidth().padding(top=8.dp)){Text("Tampilkan QR Serah Terima")}
   }}}
  }
  ErrorText(vm)
 }
 if(qr!=null)AlertDialog(onDismissRequest={qr=null},title={Text("QR Serah Terima")},text={Column(horizontalAlignment=Alignment.CenterHorizontally){Image(qr!!.asImageBitmap(),null,Modifier.size(230.dp));Text("Berlaku ${countdown/60}:${(countdown%60).toString().padStart(2,'0')}");Text("Minta kode baru jika QR kedaluwarsa.")}},confirmButton={TextButton(onClick={qr=null}){Text("Tutup")}})
 completeReservation?.let{r->AlertDialog(onDismissRequest={completeReservation=null},title={Text("Selesaikan serah terima")},text={Column{Text("Masukkan token yang dipindai dari QR penerima.");EcoField("Token QR",token){token=it};EcoField("Berat aktual (kg)",actual){actual=it}}},confirmButton={TextButton(onClick={val n=actual.toDoubleOrNull();if(n!=null)vm.complete(r.id,token,n){completeReservation=null;success=true}}){Text("Selesaikan")}},dismissButton={TextButton(onClick={completeReservation=null}){Text("Batal")}})}
 if(success)AlertDialog(onDismissRequest={success=false},title={Text("Serah terima selesai")},text={Text("Transaksi berhasil dicatat dan dampak EcoLoop telah diperbarui.")},confirmButton={TextButton(onClick={success=false}){Text("Selesai")}})
}
private fun makeQr(value:String):Bitmap{val matrix=MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,640,640);return Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888).also{b->for(x in 0 until 640)for(y in 0 until 640)b.setPixel(x,y,if(matrix[x,y])AndroidColor.BLACK else AndroidColor.WHITE)}}
@Composable fun ImpactScreen(vm:EcoViewModel){val impact by vm.impact.collectAsState();Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)){Text("Dampak akunmu",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Kontribusi terverifikasi untuk SDG 12.5",color=Color.Gray);Spacer(Modifier.height(20.dp));MetricCard("%.2f kg".format(impact.totalKg),"Material tersalurkan");MetricCard("${impact.transferCount}","Transaksi selesai");Text("Dengan menggunakan kembali dan mendaur ulang material, kamu membantu mengurangi limbah dan mendukung konsumsi yang bertanggung jawab.",Modifier.padding(top=20.dp))}}
@Composable private fun MetricCard(value:String,label:String){Card(Modifier.fillMaxWidth().padding(bottom=12.dp),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(22.dp)){Text(label,color=Color.Gray);Text(value,fontSize=34.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}}}
@Composable fun ProfileScreen(vm:EcoViewModel){val user by vm.user.collectAsState();var confirm by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(22.dp)){Text("Profil",fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Text(user?.name?:"Pengguna EcoLoop",fontSize=22.sp,fontWeight=FontWeight.SemiBold);Text(user?.email?:"",color=Color.Gray);Text("${user?.district.orEmpty()}, ${user?.city?:"DKI Jakarta"}",Modifier.padding(top=12.dp));Button(onClick={confirm=true},Modifier.fillMaxWidth().padding(top=32.dp)){Text("Keluar")}};if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Keluar dari EcoLoop?")},text={Text("Kamu perlu masuk kembali untuk mengakses akun.")},confirmButton={TextButton(onClick={confirm=false;vm.logout()}){Text("Keluar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Batal")}})}
