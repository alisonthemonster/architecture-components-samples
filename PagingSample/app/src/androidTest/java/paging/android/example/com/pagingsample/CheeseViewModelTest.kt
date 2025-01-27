package paging.android.example.com.pagingsample

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheeseViewModelTest {
    private val testDispatcher = TestCoroutineDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun separatorsTest() = runBlockingTest(testDispatcher) {
        val cheeses = listOf(
            Cheese(name = "Abbaye de Belloc"),
            Cheese(name = "Brie"),
            Cheese(name = "Cheddar"),
        )
        val differ = AsyncPagingDataDiffer(
            diffCallback = CheeseAdapter.diffCallback,
            updateCallback = noopListUpdateCallback,
            mainDispatcher = testDispatcher,
            workerDispatcher = testDispatcher,
        )
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val db = Room.inMemoryDatabaseBuilder(appContext, CheeseDb::class.java)
                .allowMainThreadQueries()
                .build()
        val cheeseDao = db.cheeseDao()

        val cheeseViewModel = CheeseViewModel(cheeseDao)

        // submitData allows differ to receive data from PagingData, but suspends until
        // invalidation, so we must launch this in a separate job.
        val job = launch {
            cheeseViewModel.allCheeses.collectLatest { pagingData ->
                differ.submitData(pagingData)
            }
        }

        // Wait for initial load to finish.
        advanceUntilIdle()

        assertThat(differ.snapshot()).isEmpty()

        //add cheeses to the database
        cheeseDao.insert(cheeses)

        val job2 = launch {
            cheeseViewModel.allCheeses.collectLatest { pagingData ->
                differ.submitData(pagingData)
            }
        }

        advanceUntilIdle()

        assertThat(differ.snapshot()).containsExactly(
            CheeseListItem.Separator('A'),
            CheeseListItem.Item(cheeses[0]),
            CheeseListItem.Separator('B'),
            CheeseListItem.Item(cheeses[1]),
            CheeseListItem.Separator('C'),
            CheeseListItem.Item(cheeses[2]),
        )

        // runBlockingTest checks for leaking jobs, so we have to cancel the ones we started.
        job.cancel()
        job2.cancel()
    }
}

class CheeseDaoFake(val cheeses: List<Cheese>) : CheeseDao {
    override fun allCheesesByName(): PagingSource<Int, Cheese> {
        return object : PagingSource<Int, Cheese>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Cheese> {
                return LoadResult.Page(
                    data = cheeses,
                    prevKey = null,
                    nextKey = null,
                )
            }

            // Ignored in test.
            override fun getRefreshKey(state: PagingState<Int, Cheese>): Int? = null
        }
    }

    override fun insert(cheeses: List<Cheese>) {}
    override fun insert(cheese: Cheese) {}
    override fun delete(cheese: Cheese) {}
}

val noopListUpdateCallback = object : ListUpdateCallback {
    override fun onInserted(position: Int, count: Int) {}
    override fun onRemoved(position: Int, count: Int) {}
    override fun onMoved(fromPosition: Int, toPosition: Int) {}
    override fun onChanged(position: Int, count: Int, payload: Any?) {}
}
