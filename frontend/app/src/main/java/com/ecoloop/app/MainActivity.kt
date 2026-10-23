package com.ecoloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.HttpException
import com.google.gson.JsonParser
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

private val android.content.Context.store by preferencesDataStore("ecoloop_session")
private val TOKEN = stringPreferencesKey("jwt")
val categories = listOf("Kain Perca", "Kardus Bersih", "Botol Plastik", "Potongan Kayu")
val districts = listOf("Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Jakarta Utara")

data class User(val id:String="", val name:String="", val email:String="", val contact:String?=null, val city:String="Jakarta", val district:String="")
data class Listing(val id:String="", val title:String="Material tersedia", val category:String="Kain Perca", val description:String="", val availableKg:Double=0.0, val totalKg:Double=0.0, val district:String="Jakarta Selatan", val imageUrl:String?=null, val photos:List<String> = emptyList(), val categoryId:String="", val supplier:User?=null) {
 val displayPhoto:String get() = photos.firstOrNull()?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() } ?: defaultPhotoForCategory(title,categoryId)
 val displayCategory:String get() = category.takeIf { it.isNotBlank() } ?: categoryNameFor(title,categoryId)
}

fun defaultPhotoForCategory(title:String,categoryId:String):String {
 val key=("$title $categoryId").lowercase()
 val photo=when {
  listOf("kain","perca","fabric","textile").any { it in key } -> "photo-1558618666-fcd25c85cd64"
  listOf("kardus","cardboard","karton").any { it in key } -> "photo-1532996122724-e3c354a0b15b"
  listOf("botol","bottle","plastik","plastic").any { it in key } -> "photo-1605600659873-d808a13e4d9a"
  listOf("kayu","wood","timber").any { it in key } -> "photo-1513467655676-561b7d489a88"
  else -> "photo-1558618666-fcd25c85cd64"
 }
 return "https://images.unsplash.com/$photo?auto=format&fit=crop&w=1200&q=85"
}
fun categoryNameFor(title:String,categoryId:String):String {
 val key=("$title $categoryId").lowercase()
 return when {
  listOf("kain","perca","fabric","textile").any { it in key } -> "Kain Perca"
  listOf("kardus","cardboard","karton").any { it in key } -> "Kardus Bersih"
  listOf("botol","bottle","plastik","plastic").any { it in key } -> "Botol Plastik"
  listOf("kayu","wood","timber").any { it in key } -> "Potongan Kayu"
  else -> "Material daur ulang"
 }
}
data class Reservation(val id:String="", val status:String="REQUESTED", val requestedKg:Double=0.0, val listing:Listing?=null, val receiver:User?=null, val handoverToken:String?=null)
data class Impact(val totalKg:Double=0.0, val transferCount:Int=0, val suppliedKg:Double=0.0, val receivedKg:Double=0.0)
data class DataResponse<T>(val data:T)
data class ListingPage(val items:List<Listing> = emptyList(), val page:Int=1, val limit:Int=20, val total:Int=0)
data class HandoverToken(val token:String="", val expiresAt:String="")
data class AuthRequest(val email:String, val password:String, val name:String?=null, val city:String?=null, val district:String?=null)
data class AuthResponse(val token:String="", val user:User?=null)
data class ReservationRequest(val listingId:String, val requestedKg:Double)
data class ListingRequest(val title:String,val category:String,val totalKg:Double,val district:String,val description:String,val imageUrl:String)
data class HandoverRequest(val handoverToken:String,val actualKg:Double)
interface EcoApi {
 @POST("auth/login") suspend fun login(@Body body:AuthRequest):DataResponse<AuthResponse>
 @POST("auth/register") suspend fun register(@Body body:AuthRequest):DataResponse<AuthResponse>
 @GET("me") suspend fun me():DataResponse<User>
 @GET("listings") suspend fun listings(@Query("q") q:String?=null,@Query("category") category:String?=null,@Query("district") district:String?=null):DataResponse<ListingPage>
 @POST("listings") suspend fun addListing(@Body body:ListingRequest):DataResponse<Listing>
 @POST("reservations") suspend fun reserve(@Body body:ReservationRequest):DataResponse<Reservation>
 @GET("reservations") suspend fun reservations(@Query("view") view:String):DataResponse<List<Reservation>>
 @POST("reservations/{id}/approve") suspend fun approve(@retrofit2.http.Path("id") id:String):DataResponse<Reservation>
 @POST("reservations/{id}/reject") suspend fun reject(@retrofit2.http.Path("id") id:String):DataResponse<Reservation>
 @POST("reservations/{id}/handover-token") suspend fun handover(@retrofit2.http.Path("id") id:String):DataResponse<HandoverToken>
 @POST("reservations/{id}/complete") suspend fun complete(@retrofit2.http.Path("id") id:String,@Body body:HandoverRequest):DataResponse<Reservation>
 @GET("impact/me") suspend fun impact():DataResponse<Impact>
}
class EcoRepository(private val context:android.content.Context) {
 private val client=OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
  val token=runCatching { kotlinx.coroutines.runBlocking { context.store.data.first()[TOKEN] } }.getOrNull()
  chain.proceed(chain.request().newBuilder().apply { if(!token.isNullOrBlank()) addHeader("Authorization","Bearer $token") }.build())
 }).connectTimeout(15,TimeUnit.SECONDS).build()
 val api:EcoApi=Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(EcoApi::class.java)
 suspend fun saveToken(token:String){context.store.edit { it[TOKEN]=token }}
 suspend fun clear(){context.store.edit { it.remove(TOKEN) }}
 suspend fun hasToken()=context.store.data.first()[TOKEN]!=null
}
class EcoViewModel(private val repo:EcoRepository):ViewModel(){
 private val _list=MutableStateFlow<List<Listing>>(emptyList()); val list=_list.asStateFlow()
 private val _incoming=MutableStateFlow<List<Reservation>>(emptyList()); val incoming=_incoming.asStateFlow()
 private val _outgoing=MutableStateFlow<List<Reservation>>(emptyList()); val outgoing=_outgoing.asStateFlow()
 private val _impact=MutableStateFlow(Impact()); val impact=_impact.asStateFlow()
 private val _user=MutableStateFlow<User?>(null); val user=_user.asStateFlow()
 private val _error=MutableStateFlow<String?>(null); val error=_error.asStateFlow()
 private val _authenticated=MutableStateFlow(false); val authenticated=_authenticated.asStateFlow()
 init { viewModelScope.launch { _authenticated.value=repo.hasToken(); if(_authenticated.value){runCatching{_user.value=repo.api.me().data}; refresh()} } }
 fun run(block:suspend()->Unit){viewModelScope.launch { _error.value=null; runCatching { block() }.onFailure { e -> _error.value=if(e is HttpException) parseApiError(e) else (e.message ?: "Terjadi kendala. Coba lagi.") } } }
 private fun parseApiError(e:HttpException):String { val body=runCatching{e.response()?.errorBody()?.string()}.getOrNull(); val message=runCatching{JsonParser.parseString(body).asJsonObject.getAsJsonObject("error").get("message")?.asString}.getOrNull(); return message ?: when(e.code()){401->"Sesi tidak valid atau telah berakhir. Silakan masuk kembali.";403->"Anda tidak memiliki izin untuk melakukan tindakan ini.";404->"Data tidak ditemukan.";409->"Data berubah atau stok tidak mencukupi. Muat ulang dan coba lagi.";else->"Permintaan gagal (${e.code()}). Silakan coba lagi."} }
 fun auth(email:String,password:String,name:String?=null,district:String?=null,register:Boolean=false){run { val r=if(register)repo.api.register(AuthRequest(email,password,name,"DKI Jakarta",district)) else repo.api.login(AuthRequest(email,password)); if(r.data.token.isBlank()) error("Token tidak diterima dari server.");repo.saveToken(r.data.token);_user.value=r.data.user?:repo.api.me().data;_authenticated.value=true;refresh() }}
 fun logout(){run{repo.clear();_authenticated.value=false;_user.value=null}}
 fun refresh(){run { _list.value=repo.api.listings().data.items; _incoming.value=repo.api.reservations("incoming").data;_outgoing.value=repo.api.reservations("outgoing").data;_impact.value=repo.api.impact().data }}
 fun search(q:String,category:String?,district:String?){run{_list.value=repo.api.listings(q.ifBlank{null},category,district).data.items}}
 fun reserve(item:Listing,kg:Double)=run { require(kg>0&&kg<=item.availableKg) { "Berat harus lebih dari 0 dan tidak melebihi stok." };repo.api.reserve(ReservationRequest(item.id,kg));refresh() }
 fun listing(r:ListingRequest)=run{repo.api.addListing(r);refresh()}
 fun approve(id:String,yes:Boolean)=run{if(yes)repo.api.approve(id) else repo.api.reject(id);refresh()}
 fun handover(id:String,done:(String)->Unit)=run{done(repo.api.handover(id).data.token);refresh()}
 fun complete(id:String,token:String,kg:Double)=run{repo.api.complete(id,HandoverRequest(token,kg));refresh()}
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF167A57),secondary=Color(0xFFB9DCCB),background=Color(0xFFF6F8F4))) { val context= LocalContext.current; val vm:EcoViewModel= viewModel(factory=object:androidx.lifecycle.ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=EcoViewModel(EcoRepository(context)) as T}); EcoApp(vm) } } }}

@Composable fun EcoApp(vm:EcoViewModel){
 val auth by vm.authenticated.collectAsState(); val nav= rememberNavController(); var selected by remember { mutableStateOf<Listing?>(null) }
 if(!auth){AuthScreen(vm);return}
 val routes=listOf("home","activity","add","impact","profile"); val labels=listOf("Beranda","Aktivitas","Tambah","Dampak","Profil"); val icons=listOf(Icons.Default.Home,Icons.Default.List,Icons.Default.Add,Icons.Default.Insights,Icons.Default.Person)
 Scaffold(bottomBar={NavigationBar { routes.forEachIndexed { i,r -> NavigationBarItem(selected=nav.currentDestination?.route==r,onClick={nav.navigate(r){popUpTo("home");launchSingleTop=true}},icon={Icon(icons[i],null)},label={Text(labels[i])}) } }}) { padding -> NavHost(nav,"home",Modifier.padding(padding)){ composable("home"){HomeScreen(vm){selected=it;nav.navigate("detail")}}; composable("detail"){selected?.let{DetailScreen(it,vm)}};composable("activity"){ActivityScreen(vm)};composable("add"){AddListingScreen(vm)};composable("impact"){ImpactScreen(vm)};composable("profile"){ProfileScreen(vm)} } }
}
@Composable private fun AuthScreen(vm:EcoViewModel){var register by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var district by remember{mutableStateOf(districts.first())}; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center){Text("EcoLoop",fontSize=36.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Text("Material sisa, peluang baru.",color=Color.Gray);Spacer(Modifier.height(28.dp));if(register){Field("Nama",name){name=it};DistrictPicker(district){district=it}};Field("Email",email){email=it};Field("Password",pass,true){pass=it};Button(onClick={vm.auth(email,pass,name,district,register)},Modifier.fillMaxWidth().padding(top=12.dp)){Text(if(register)"Buat akun" else "Masuk")};TextButton(onClick={register=!register}){Text(if(register)"Sudah punya akun? Masuk" else "Belum punya akun? Daftar")};ErrorText(vm)} }
@Composable private fun HomeScreen(vm:EcoViewModel,onSelect:(Listing)->Unit){val list by vm.list.collectAsState();var query by remember{mutableStateOf("")};var cat by remember{mutableStateOf<String?>(null)};var area by remember{mutableStateOf<String?>(null)};Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){Text("Temukan material",fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=20.dp));Text("Beri bahan sisa kesempatan kedua.",color=Color.Gray);OutlinedTextField(query,{query=it;vm.search(query,cat,area)},Modifier.fillMaxWidth().padding(top=14.dp),placeholder={Text("Cari kain, kardus, botol...")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true);Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=8.dp)){(listOf("Semua")+categories).forEach{FilterChip(selected=(if(it=="Semua")cat==null else cat==it),onClick={cat=if(it=="Semua")null else it;vm.search(query,cat,area)},label={Text(it)},modifier=Modifier.padding(end=6.dp))}};Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(selected=area==null,onClick={area=null;vm.search(query,cat,area)},label={Text("Semua kecamatan")});districts.forEach{FilterChip(selected=area==it,onClick={area=it;vm.search(query,cat,area)},label={Text(it)},modifier=Modifier.padding(start=5.dp))}};LazyColumn(contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){item->ListingCard(item){onSelect(item)}}} };ErrorText(vm) }
@Composable private fun ListingCard(item:Listing,onClick:()->Unit){Card(onClick=onClick,Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column{AsyncImage(model=item.displayPhoto,contentDescription=item.title,contentScale=androidx.compose.ui.layout.ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(170.dp));Column(Modifier.padding(16.dp)){Text(item.displayCategory.uppercase(),color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(item.title,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Text("${item.district} · %.2f kg tersedia".format(item.availableKg),color=Color.Gray)}}} }
@Composable private fun DetailScreen(item:Listing,vm:EcoViewModel){var show by remember{mutableStateOf(false)};var kg by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){ListingCard(item){};Spacer(Modifier.height(16.dp));Text("Tentang material",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(item.description.ifBlank{"Material ini siap dimanfaatkan kembali."},Modifier.padding(vertical=8.dp));Text("Penyedia: ${item.supplier?.name?:"Komunitas EcoLoop"}");Button(onClick={show=true},Modifier.fillMaxWidth().padding(top=24.dp)){Text("Ajukan Reservasi")}};if(show)AlertDialog(onDismissRequest={show=false},title={Text("Ajukan reservasi")},text={Column{Text("Stok tersedia ${item.availableKg} kg");OutlinedTextField(kg,{kg=it},label={Text("Berat yang dibutuhkan (kg)")})}},confirmButton={TextButton(onClick={kg.toDoubleOrNull()?.let{vm.reserve(item,it);show=false}}){Text("Kirim permintaan")}},dismissButton={TextButton(onClick={show=false}){Text("Batal")}});ErrorText(vm)}